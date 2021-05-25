/*
 * Copyright (C) 2021 omegazero.org
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Covered Software is provided under this License on an "as is" basis, without warranty of any kind,
 * either expressed, implied, or statutory, including, without limitation, warranties that the Covered Software
 * is free of defects, merchantable, fit for a particular purpose or non-infringing.
 * The entire risk as to the quality and performance of the Covered Software is with You.
 */
package org.omegazero.net.socket.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.omegazero.net.socket.InetSocketConnection;

public class TLSConnection extends InetSocketConnection {

	private final SSLContext sslContext;
	private final Consumer<Runnable> taskRunner;
	private final String[] alpnNames;

	private final SSLEngine sslEngine;

	protected ByteBuffer readBufUnwrapped;
	protected ByteBuffer writeBufUnwrapped;

	private boolean handshakeComplete = false;

	private String alpnProtocol = null;

	public TLSConnection(SocketChannel socket, SSLContext sslContext, boolean client, Consumer<Runnable> taskRunner, String[] alpnNames) throws IOException {
		this(socket, null, sslContext, client, taskRunner, alpnNames, null);
	}

	public TLSConnection(SocketChannel socket, InetSocketAddress remote, SSLContext sslContext, boolean client, Consumer<Runnable> taskRunner, String[] alpnNames,
			String[] requestedServerNames) throws IOException {
		super(socket, remote);
		this.sslContext = sslContext;
		this.taskRunner = taskRunner;
		this.alpnNames = alpnNames;

		this.sslEngine = this.sslContext.createSSLEngine();
		this.sslEngine.setUseClientMode(client);
		this.sslEngine.setHandshakeApplicationProtocolSelector((sslEngine, list) -> {
			if(TLSConnection.this.alpnNames != null){
				for(String s : TLSConnection.this.alpnNames){
					if(list.contains(s))
						return s;
				}
			}else if(list.size() > 0)
				return list.get(0);
			return null;
		});

		// GCM ciphers are broken or something (or im doing something wrong which is far more likely)
		// if data is received immediately after the handshake completed, the next read call will cause a "AEADBadTagException: Tag mismatch!"
		String[] ciphersList = this.sslEngine.getEnabledCipherSuites();
		List<String> ciphers = new ArrayList<>(ciphersList.length);
		for(String s : ciphersList){
			if(!s.contains("GCM"))
				ciphers.add(s);
		}
		this.sslEngine.setEnabledCipherSuites(ciphers.toArray(new String[ciphers.size()]));

		if(requestedServerNames != null){
			SNIServerName[] serverNames = new SNIServerName[requestedServerNames.length];
			for(int i = 0; i < requestedServerNames.length; i++){
				serverNames[i] = new SNIHostName(requestedServerNames[i]);
			}
			this.sslEngine.getSSLParameters().setServerNames(Arrays.asList(serverNames));
		}

		this.createBuffers();
	}


	@Override
	protected void createBuffers() {
		super.readBuf = ByteBuffer.allocate(this.sslEngine.getSession().getPacketBufferSize());
		super.writeBuf = ByteBuffer.allocate(this.sslEngine.getSession().getPacketBufferSize());
		this.readBufUnwrapped = ByteBuffer.allocate(this.sslEngine.getSession().getApplicationBufferSize());
		this.writeBufUnwrapped = ByteBuffer.allocate(this.sslEngine.getSession().getApplicationBufferSize());
	}


	@Override
	public synchronized byte[] read() {
		try{
			if(!super.isConnected())
				return null;
			super.readBuf.clear();
			if(handshakeComplete){
				return this.readApplicationData();
			}else{
				this.doTLSHandshake();
			}
		}catch(Throwable e){
			super.handleError(e);
		}
		return null;
	}

	@Override
	public synchronized void write(byte[] a) {
		try{
			if(!this.handshakeComplete){
				super.queueWrite(a);
				return;
			}
			this.writeBufUnwrapped.clear();
			int written = 0;
			while(written < a.length){
				int wr = Math.min(this.writeBufUnwrapped.remaining(), a.length - written);
				this.writeBufUnwrapped.put(a, written, wr);
				this.writeBufUnwrapped.flip();

				while(this.writeBufUnwrapped.hasRemaining()){
					super.writeBuf.clear();
					SSLEngineResult result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
					if(result.getStatus() == Status.OK){
						super.writeBuf.flip();
						super.writeToSocket();
					}else
						throw new SSLException("Write SSL wrap failed: " + result.getStatus());
				}

				this.writeBufUnwrapped.compact();
				written += wr;
			}
		}catch(Throwable e){
			super.handleError(e);
		}
	}

	@Override
	public synchronized void close() {
		this.sslEngine.closeOutbound();
		super.close();
	}


	/**
	 * 
	 * @return The negotiated ALPN protocol name, or <code>null</code> if ALPN did not occur
	 */
	public String getAlpnProtocol() {
		// an empty string means that no ALPN happened (as specified by SSLEngine.getApplicationProtocol())
		if(alpnProtocol == null || alpnProtocol.length() < 1)
			return null;
		else
			return alpnProtocol;
	}


	private byte[] readApplicationData() throws IOException {
		int read = super.readFromSocket();
		if(read > 0){
			super.readBuf.flip();
			SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
			if(result.getStatus() == Status.CLOSED)
				this.close();
			else if(result.getStatus() == Status.OK){
				this.readBufUnwrapped.flip();
				if(this.readBufUnwrapped.hasRemaining()){
					byte[] a = new byte[this.readBufUnwrapped.remaining()];
					this.readBufUnwrapped.get(a);
					this.readBufUnwrapped.compact();
					return a;
				}
			}else
				throw new SSLException("Read SSL unwrap failed: " + result.getStatus());
		}else if(read < 0){
			this.close();
		}
		return null;
	}

	public void doTLSHandshake() throws IOException {
		if(this.handshakeComplete)
			throw new IllegalStateException("Handshake is already completed");
		HandshakeStatus status = sslEngine.getHandshakeStatus();
		if(status == HandshakeStatus.NOT_HANDSHAKING){
			sslEngine.beginHandshake();
			status = sslEngine.getHandshakeStatus();
		}
		while(true){
			if(status == HandshakeStatus.NEED_UNWRAP){
				int read = super.readFromSocket();

				if(read < 0)
					throw new SSLHandshakeException("Socket disconnected before handshake completed");
				super.readBuf.flip();
				SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
				super.readBuf.compact();
				// BUFFER_UNDERFLOW here means no data is available; for some reason we can't just immediately return when we read 0 bytes above,
				// because then SSLEngine will break; instead, cause buffer underflow and loop around to return here
				if(read == 0 && result.getStatus() == Status.BUFFER_UNDERFLOW)
					return;
				else if(result.getStatus() != Status.OK)
					throw new SSLHandshakeException("Unexpected status after SSL unwrap: " + result.getStatus());
				status = sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NEED_WRAP){
				super.writeBuf.clear();
				SSLEngineResult result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
				if(result.getStatus() == Status.OK){
					super.writeBuf.flip();
					super.writeToSocket();
				}else
					throw new SSLHandshakeException("Unexpected status after SSL wrap: " + result.getStatus());
				status = sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NEED_TASK){
				Runnable r;
				while((r = this.sslEngine.getDelegatedTask()) != null){
					this.taskRunner.accept(r);
				}
				status = sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.FINISHED || status == HandshakeStatus.NOT_HANDSHAKING){
				this.handshakeComplete = true;
				this.alpnProtocol = this.sslEngine.getApplicationProtocol();
				super.handleConnect();
				return;
			}else
				throw new SSLHandshakeException("Unexpected engine status: " + status);
		}
	}
}
