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
package org.omegazero.net.nio.socket;

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
import javax.net.ssl.SSLParameters;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.common.util.PropertyUtil;
import org.omegazero.net.nio.socket.provider.ChannelProvider;
import org.omegazero.net.socket.TLSConnection;

/**
 * A {@link ChannelConnection} with {@linkplain TLSConnection TLS} encryption. This implementation uses an {@link SSLEngine} from a given {@link SSLContext} for TLS support.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.socket.impl} and called {@code TLSConnection}.
 */
public class NioTLSConnection extends ChannelConnection implements TLSConnection {

	private static final Logger logger = LoggerUtil.createLogger();

	private static final int minTLSVersion = PropertyUtil.getInt("org.omegazero.net.tls.minTLSVersion", 2);
	private static final boolean disableWeakCiphers = PropertyUtil.getBoolean("org.omegazero.net.tls.disableWeakCiphers", false);

	private final SSLContext sslContext;

	private final SSLEngine sslEngine;

	protected ByteBuffer readBufUnwrapped;
	protected ByteBuffer writeBufUnwrapped;

	private boolean handshakeComplete = false;

	public NioTLSConnection(SelectionKey selectionKey, ChannelProvider provider, SSLContext sslContext, boolean client, String[] alpnNames) throws IOException {
		this(selectionKey, provider, null, sslContext, client, alpnNames, null);
	}

	public NioTLSConnection(SelectionKey selectionKey, ChannelProvider provider, SocketAddress remote, SSLContext sslContext, boolean client, String[] alpnNames,
			String[] requestedServerNames) throws IOException {
		super(selectionKey, provider, remote);
		this.sslContext = sslContext;

		if(super.getRemoteAddress() != null && super.getRemoteAddress() instanceof InetSocketAddress){
			InetSocketAddress inaddr = (InetSocketAddress) super.getRemoteAddress();
			this.sslEngine = this.sslContext.createSSLEngine(inaddr.getAddress().getHostAddress(), inaddr.getPort());
		}else
			this.sslEngine = this.sslContext.createSSLEngine();
		this.sslEngine.setUseClientMode(client);

		if(minTLSVersion >= 0){
			String[] protoList = this.sslEngine.getEnabledProtocols();
			int ti = 0;
			for(int i = 0; i < protoList.length; i++){
				if(isMinTLSVersion(protoList[i]))
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
				if(!isWeakCipher(cipherList[i]))
					cipherList[ti++] = cipherList[i];
			}
			if(ti < cipherList.length){
				String[] ncipherList = new String[ti];
				System.arraycopy(cipherList, 0, ncipherList, 0, ti);
				this.sslEngine.setEnabledCipherSuites(ncipherList);
				logger.trace("Reduced set of enabled cipher suites from ", cipherList.length, " to ", ti);
			}
		}

		SSLParameters sslParams = this.sslEngine.getSSLParameters();

		if(alpnNames != null)
			sslParams.setApplicationProtocols(alpnNames);

		if(requestedServerNames != null){
			SNIServerName[] serverNames = new SNIServerName[requestedServerNames.length];
			for(int i = 0; i < requestedServerNames.length; i++){
				serverNames[i] = new SNIHostName(requestedServerNames[i]);
			}
			sslParams.setServerNames(Arrays.asList(serverNames));
		}

		this.sslEngine.setSSLParameters(sslParams);

		this.createBuffers();
	}


	@Override
	protected void createBuffers() {
		super.readBuf = ByteBuffer.allocateDirect(this.sslEngine.getSession().getPacketBufferSize() >> 3);
		super.writeBuf = ByteBuffer.allocateDirect(this.sslEngine.getSession().getPacketBufferSize());
		this.readBufUnwrapped = ByteBuffer.allocateDirect(this.sslEngine.getSession().getApplicationBufferSize() >> 3);
		this.writeBufUnwrapped = ByteBuffer.allocateDirect(this.sslEngine.getSession().getApplicationBufferSize() >> 2);
	}


	@Override
	public byte[] read() {
		try{
			if(!super.isConnected())
				return null;
			synchronized(super.readLock){
				if(this.handshakeComplete){
					int read = super.readFromSocket();
					if(read > 0){
						super.readBuf.flip();
						return this.readApplicationData();
					}else if(read < 0){
						super.close0();
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
	public void write(byte[] data, int offset, int length) {
		super.writeBuffered(data, offset, length, true, this.writeBufUnwrapped, this::writeWrapped);
	}

	@Override
	public void writeQueue(byte[] data, int offset, int length) {
		super.writeBuffered(data, offset, length, false, this.writeBufUnwrapped, this::writeWrapped);
	}

	@Override
	public boolean flush() {
		super.writeBuffered(null, 0, 0, true, this.writeBufUnwrapped, this::writeWrapped);
		return super.flush();
	}

	private void writeWrapped() throws IOException {
		super.writeBuf.clear();
		while(this.writeBufUnwrapped.hasRemaining()){
			int beforeDataLen = this.writeBufUnwrapped.remaining();
			SSLEngineResult result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
			if(result.getStatus() == Status.OK){
				if(this.writeBufUnwrapped.remaining() >= beforeDataLen)
					throw new IOException("wrap returned OK but no data was written");
				super.writeBuf.flip();
				if(!super.writeBuf.hasRemaining()){
					this.writeBufUnwrapped.clear();
					throw new IOException("writeBuf is empty after wrap");
				}
				super.writeToSocket();
				super.writeBuf.clear();
			}else if(result.getStatus() == Status.BUFFER_OVERFLOW){
				this.increaseBufferSize(3);
			}else
				throw new SSLException("Write SSL wrap failed: " + result.getStatus());
		}
	}

	@Override
	protected void close0() {
		this.closeOutbound();
		super.close0();
	}

	@Override
	public boolean isConnected() {
		return super.isConnected() && this.handshakeComplete;
	}

	@Override
	public boolean isSocketConnected() {
		return super.isConnected();
	}

	@Override
	public String getProtocol() {
		return this.sslEngine.getSession().getProtocol();
	}

	@Override
	public String getCipher() {
		return this.sslEngine.getSession().getCipherSuite();
	}

	@Override
	public String getApplicationProtocol() {
		String proto;
		try{
			proto = this.sslEngine.getApplicationProtocol();
		}catch(UnsupportedOperationException e){
			return null;
		}
		if(proto == null || proto.length() < 1)
			return null;
		else
			return proto;
	}


	private void increaseBufferSize(int which) throws BufferOverflowException {
		if(which == 0){
			assert Thread.holdsLock(super.readLock);
			this.readBufUnwrapped = this.increaseBufferSize0(this.readBufUnwrapped, this.sslEngine.getSession().getApplicationBufferSize() << 1, 3);
		}else if(which == 1){
			this.writeBufUnwrapped = this.increaseBufferSize0(this.writeBufUnwrapped, this.sslEngine.getSession().getApplicationBufferSize(), 1);
		}else if(which == 2){
			assert Thread.holdsLock(super.readLock);
			super.readBuf = this.increaseBufferSize0(super.readBuf, this.sslEngine.getSession().getPacketBufferSize(), 1);
		}else if(which == 3){
			super.writeBuf = this.increaseBufferSize0(super.writeBuf, this.sslEngine.getSession().getPacketBufferSize() << 1, 1);
		}else
			throw new IllegalArgumentException("which == " + which);
	}

	private ByteBuffer increaseBufferSize0(ByteBuffer buf, int maxSize, int increase2Pow) throws BufferOverflowException {
		if(buf.capacity() >= maxSize)
			throw new BufferOverflowException();
		int newSize = Math.min(maxSize, buf.capacity() << increase2Pow);
		ByteBuffer newBuf = ByteBuffer.allocateDirect(newSize);
		buf.flip();
		newBuf.put(buf);
		return newBuf;
	}

	private void closeOutbound() {
		this.sslEngine.closeOutbound();
		if(super.isConnected()){
			synchronized(super.writeLock){
				try{
					int count = 0;
					SSLEngineResult result;
					super.writeBuf.clear();
					do{
						result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
						if(result.getStatus() == Status.BUFFER_OVERFLOW){
							this.increaseBufferSize(3);
							continue;
						}
						super.writeBuf.flip();
						int written = super.writeToSocket();
						logger.debug("Wrote SSL close message (", written, " bytes, status ", result.getStatus(), ")");
						if(count++ >= 15){
							logger.warn("Wrote ", count, " SSL close messages to ", this.getRemoteAddress(), ", aborting");
							break;
						}
						super.writeBuf.clear();
					}while(result.getStatus() == Status.OK || result.getStatus() == Status.BUFFER_OVERFLOW);
				}catch(IOException e){
					logger.debug("Error while writing SSL close message: ", e.toString());
				}
			}
		}
	}

	private byte[] readApplicationData() throws IOException {
		assert Thread.holdsLock(super.readLock);
		this.readBufUnwrapped.clear();
		while(super.readBuf.hasRemaining()){
			int beforeDataLen = super.readBuf.remaining();
			SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
			if(result.getStatus() == Status.CLOSED){
				this.closeOutbound();
				break;
			}else if(result.getStatus() == Status.OK){
				if(super.readBuf.remaining() >= beforeDataLen)
					throw new IOException("unwrap returned OK but no data was read");
				continue;
			}else if(result.getStatus() == Status.BUFFER_UNDERFLOW){
				break;
			}else if(result.getStatus() == Status.BUFFER_OVERFLOW){
				this.increaseBufferSize(0);
			}else
				throw new SSLException("Read SSL unwrap failed: " + result.getStatus());
		}
		super.readBuf.compact();
		if(!super.readBuf.hasRemaining()){
			this.increaseBufferSize(2);
		}
		this.readBufUnwrapped.flip();
		if(this.readBufUnwrapped.hasRemaining()){
			byte[] a = new byte[this.readBufUnwrapped.remaining()];
			this.readBufUnwrapped.get(a);
			return a;
		}
		return null;
	}

	public void doTLSHandshake() throws IOException {
		assert Thread.holdsLock(super.readLock);
		if(this.handshakeComplete)
			throw new IllegalStateException("Handshake is already completed");
		HandshakeStatus status = this.sslEngine.getHandshakeStatus();
		if(status == HandshakeStatus.NOT_HANDSHAKING){
			this.sslEngine.beginHandshake();
			status = this.sslEngine.getHandshakeStatus();
		}
		while(true){
			boolean statusNUA = status.name().equals("NEED_UNWRAP_AGAIN") /* for java < 9 compatibility */;
			if(status == HandshakeStatus.NEED_UNWRAP || statusNUA){
				int read = super.readFromSocket();
				if(read < 0)
					throw new EOFException("Socket disconnected before handshake completed");
				int beforeRemaining = super.readBuf.remaining();
				super.readBuf.flip();
				SSLEngineResult result = this.sslEngine.unwrap(super.readBuf, this.readBufUnwrapped);
				super.readBuf.compact();
				if(!super.readBuf.hasRemaining()){
					this.increaseBufferSize(2);
				}
				if(result.getStatus() == Status.BUFFER_UNDERFLOW) // wait for more data
					return;
				else if(result.getStatus() == Status.BUFFER_OVERFLOW)
					this.increaseBufferSize(0);
				else if(result.getStatus() != Status.OK)
					throw new SSLHandshakeException("Unexpected status after SSL unwrap: " + result.getStatus());
				else if(super.readBuf.remaining() <= beforeRemaining && !statusNUA) // no data is read from readBuf at NEED_UNWRAP_AGAIN
					throw new IOException("unwrap returned OK but no data was read");
				status = this.sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NEED_WRAP){
				super.writeBuf.clear();
				SSLEngineResult result = this.sslEngine.wrap(this.writeBufUnwrapped, super.writeBuf);
				if(result.getStatus() == Status.OK){
					super.writeBuf.flip();
					super.writeToSocket();
				}else if(result.getStatus() == Status.BUFFER_OVERFLOW){
					this.increaseBufferSize(3);
				}else
					throw new SSLHandshakeException("Unexpected status after SSL wrap: " + result.getStatus());
				status = this.sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NEED_TASK){
				Runnable r;
				while((r = this.sslEngine.getDelegatedTask()) != null){
					// running the tasks in a separate thread currently serves no benefit because this thread will also just block in this loop waiting for the
					// task to be completed. In that case, this thread might as well just run the task itself
					r.run();
				}
				status = this.sslEngine.getHandshakeStatus();
			}else if(status == HandshakeStatus.NOT_HANDSHAKING){
				this.handshakeComplete = true;
				if(logger.debug())
					logger.debug("SSL Handshake completed: peer=" + super.getRemoteAddress() + ", cipher=" + this.getCipher(), ", alp=", this.getApplicationProtocol());
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
