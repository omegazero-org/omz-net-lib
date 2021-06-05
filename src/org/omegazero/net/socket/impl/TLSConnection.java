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

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Arrays;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.common.util.PropertyUtil;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.provider.ChannelProvider;

public class TLSConnection extends ChannelConnection {

	private static final Logger logger = LoggerUtil.createLogger();

	private static final int minTLSVersion = PropertyUtil.getInt("org.omegazero.net.tls.minTLSVersion", 2);
	private static final boolean disableWeakCiphers = PropertyUtil.getBoolean("org.omegazero.net.tls.disableWeakCiphers", false);

	private final SSLContext sslContext;
	private final String[] alpnNames;

	private final SSLEngine sslEngine;

	protected ByteBuffer readBufUnwrapped;
	protected ByteBuffer writeBufUnwrapped;

	private boolean handshakeComplete = false;

	private String alpnProtocol = null;

	public TLSConnection(SelectionKey selectionKey, ChannelProvider provider, SSLContext sslContext, boolean client, String[] alpnNames) throws IOException {
		this(selectionKey, provider, null, sslContext, client, alpnNames, null);
	}

	public TLSConnection(SelectionKey selectionKey, ChannelProvider provider, SocketAddress remote, SSLContext sslContext, boolean client, String[] alpnNames,
			String[] requestedServerNames) throws IOException {
		super(selectionKey, provider, remote);
		this.sslContext = sslContext;
		this.alpnNames = alpnNames;

		if(super.getRemoteAddress() != null && super.getRemoteAddress() instanceof InetSocketAddress){
			InetSocketAddress inaddr = (InetSocketAddress) super.getRemoteAddress();
			this.sslEngine = this.sslContext.createSSLEngine(inaddr.getAddress().getHostAddress(), inaddr.getPort());
		}else
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

		if(minTLSVersion >= 0){
			String[] protoList = this.sslEngine.getEnabledProtocols();
			int ti = 0;
			for(int i = 0; i < protoList.length; i++){
				if(TLSConnection.isMinTLSVersion(protoList[i]))
					protoList[ti++] = protoList[i];
			}
			if(ti < protoList.length){
				String[] nprotoList = new String[ti];
				System.arraycopy(protoList, 0, nprotoList, 0, ti);
				this.sslEngine.setEnabledProtocols(nprotoList);
				logger.trace("Reduced set of enabled TLS versions from ", protoList.length, " to ", ti);
			}
		}

		if(disableWeakCiphers){
			String[] cipherList = this.sslEngine.getEnabledCipherSuites();
			int ti = 0;
			for(int i = 0; i < cipherList.length; i++){
				if(!TLSConnection.isWeakCipher(cipherList[i]))
					cipherList[ti++] = cipherList[i];
			}
			if(ti < cipherList.length){
				String[] ncipherList = new String[ti];
				System.arraycopy(cipherList, 0, ncipherList, 0, ti);
				this.sslEngine.setEnabledCipherSuites(ncipherList);
				logger.trace("Reduced set of enabled cipher suites from ", cipherList.length, " to ", ti);
			}
		}

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
		// no idea why but apparently SSLEngine requires a massive buffer for DTLS even though it only writes less than 100 bytes, otherwise BUFFER_OVERFLOW
		super.writeBuf = ByteBuffer.allocate(this.sslEngine.getSession().getPacketBufferSize() * 2);
		this.readBufUnwrapped = ByteBuffer.allocate(this.sslEngine.getSession().getApplicationBufferSize());
		this.writeBufUnwrapped = ByteBuffer.allocate(this.sslEngine.getSession().getApplicationBufferSize());
	}


	@Override
	public byte[] read() {
		try{
			if(!super.isConnected())
				return null;
			synchronized(super.readBuf){
				if(this.handshakeComplete){
					int read = super.readFromSocket();
					if(read > 0){
						super.readBuf.flip();
						return this.readApplicationData();
					}else if(read < 0){
						this.close();
					}
				}else{
					this.doTLSHandshake();
					if(this.handshakeComplete && super.readBuf.position() > 0){ // there is still data in the buffer after the handshake
						super.readBuf.flip();
						return this.readApplicationData();
					}
				}
			}
		}catch(Exception e){
			super.handleError(e);
		}
		return null;
	}

	@Override
	public void write(byte[] data) {
		try{
			synchronized(this){
				if(!super.hasConnected()){
					super.queueWrite(data);
					return;
				}
			}
			synchronized(super.writeBuf){
				this.writeBufUnwrapped.clear();
				int written = 0;
				while(written < data.length){
					int wr = Math.min(this.writeBufUnwrapped.remaining(), data.length - written);
					this.writeBufUnwrapped.put(data, written, wr);
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
			}
		}catch(Exception e){
			super.handleError(e);
		}
	}

	@Override
	public void close() {
		this.sslEngine.closeOutbound();
		this.sslEngine.getSession().invalidate();
		if(super.isConnected()){
			synchronized(super.writeBuf){
				try{
					super.writeBuf.clear();
					SSLEngineResult result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
					super.writeBuf.flip();
					int written = super.writeToSocket();
					logger.debug("Wrote SSL close message (", written, " bytes, status ", result.getStatus(), ")");
				}catch(IOException e){
					logger.debug("Error while writing SSL close message: ", e.toString());
				}
			}
		}
		super.close();
	}

	/**
	 * 
	 * @return <code>true</code> if {@link ChannelConnection#isConnected()} returns <code>true</code> and the TLS handshake has completed
	 */
	@Override
	public boolean isConnected() {
		return super.isConnected() && this.handshakeComplete;
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
		this.readBufUnwrapped.clear();
		SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
		super.readBuf.compact();
		if(!super.readBuf.hasRemaining()) // the engine requires more data than the buffer can hold (shouldnt happen because buffer is the maximum size of a TLS packet)
			throw new BufferOverflowException();
		if(result.getStatus() == Status.CLOSED)
			this.close();
		else if(result.getStatus() == Status.OK){
			this.readBufUnwrapped.flip();
			if(this.readBufUnwrapped.hasRemaining()){
				byte[] a = new byte[this.readBufUnwrapped.remaining()];
				this.readBufUnwrapped.get(a);
				return a;
			}
		}else if(result.getStatus() == Status.BUFFER_UNDERFLOW){
			return null;
		}else
			throw new SSLException("Read SSL unwrap failed: " + result.getStatus());
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
			if(status == HandshakeStatus.NEED_UNWRAP || status.toString().equals("NEED_UNWRAP_AGAIN") /* for java < 9 */){
				int read = super.readFromSocket();
				if(read < 0)
					throw new EOFException("Socket disconnected before handshake completed");
				super.readBuf.flip();
				SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
				super.readBuf.compact();
				if(!super.readBuf.hasRemaining())
					throw new BufferOverflowException();
				if(result.getStatus() == Status.BUFFER_UNDERFLOW) // wait for more data
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
					// running the tasks in a separate thread currently serves no benefit because this thread will also just block in this loop waiting for the
					// task to be completed. In that case, this thread might as well just run the task itself
					r.run();
				}
				status = sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NOT_HANDSHAKING){
				this.handshakeComplete = true;
				this.alpnProtocol = this.sslEngine.getApplicationProtocol();
				logger.debug("SSL Handshake completed: peer=" + super.getRemoteAddress() + ", cipher=" + this.sslEngine.getSession().getCipherSuite(), ", alp=",
						this.getAlpnProtocol());
				super.handleConnect();
				return;
			}else
				throw new SSLHandshakeException("Unexpected engine status: " + status);
		}
	}


	private static boolean isMinTLSVersion(String name) {
		if(name.startsWith("SSL"))
			return false;
		int di = name.indexOf('.');
		int minorV;
		if(di < 0) // TLSv1[.0]
			minorV = 0;
		else
			minorV = name.charAt(di + 1) - 48; // ASCII number 0d48 if '0'
		return minorV >= minTLSVersion;
	}

	private static boolean isWeakCipher(String name) {
		// this is only a few of the cipher families considered insecure, but this should cover most or all of ciphers in java
		return name.contains("CBC") || name.contains("ECDH_") || name.contains("RENEGOTIATION") || name.startsWith("TLS_RSA_WITH_AES_");
	}
}
