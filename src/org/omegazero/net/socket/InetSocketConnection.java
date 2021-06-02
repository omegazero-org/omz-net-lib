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
package org.omegazero.net.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;

import org.omegazero.common.event.Tasks;

/**
 * Represents an {@link InetConnection} based on a java.nio {@link SocketChannel}.
 */
public abstract class InetSocketConnection extends InetConnection {

	private final SocketChannel socket;

	private final InetSocketAddress remoteAddress;
	private final InetSocketAddress localAddress;
	private long lastIOTime;

	protected ByteBuffer readBuf;
	protected ByteBuffer writeBuf;

	private long connectTimeout = -1;

	public InetSocketConnection(SocketChannel socket) throws IOException {
		this(socket, null);
	}

	public InetSocketConnection(SocketChannel socket, InetSocketAddress remote) throws IOException {
		this.socket = socket;

		InetSocketAddress socketRemote = (InetSocketAddress) this.socket.getRemoteAddress();
		if(socketRemote != null && remote != null)
			throw new AlreadyConnectedException();
		else if(socketRemote != null)
			this.remoteAddress = socketRemote;
		else
			this.remoteAddress = remote;
		this.localAddress = (InetSocketAddress) this.socket.getLocalAddress();

		this.lastIOTime = System.currentTimeMillis();

		this.ensureNonBlocking();
	}


	protected abstract void createBuffers();


	@Override
	public void connect(int timeout) {
		try{
			this.ensureNonBlocking();
			if(this.remoteAddress == null)
				throw new UnsupportedOperationException("Cannot connect because no remote address was specified");
			boolean imm = this.socket.connect(this.remoteAddress);
			if(imm)
				super.localConnect();
			else if(timeout > 0)
				this.connectTimeout = Tasks.timeout((args) -> {
					if(!InetSocketConnection.this.isConnected()){ // definitely before this ever connected because this handler gets canceled if socket closes (or errors)
						InetSocketConnection.this.handleTimeout();
						InetSocketConnection.this.close();
					}
				}, timeout).daemon().getId();
		}catch(Exception e){
			super.handleError(e);
		}
	}

	@Override
	public void close() {
		super.localClose();
		try{
			if(this.connectTimeout >= 0)
				Tasks.clear(this.connectTimeout);
			this.socket.close();
		}catch(IOException e){
			throw new RuntimeException("Error while closing channel", e);
		}
	}

	/**
	 * 
	 * @see SocketChannel#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return this.socket.isConnected();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.remoteAddress;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return this.localAddress;
	}

	@Override
	public long getLastIOTime() {
		return this.lastIOTime;
	}


	private void ensureNonBlocking() throws IOException {
		if(this.socket.isBlocking())
			this.socket.configureBlocking(false);
	}


	protected final int readFromSocket() throws IOException {
		this.lastIOTime = System.currentTimeMillis();
		return this.socket.read(this.readBuf);
	}

	protected final int writeToSocket() throws IOException {
		this.lastIOTime = System.currentTimeMillis();
		int written = 0;
		while(this.writeBuf.hasRemaining()){
			int w = this.socket.write(this.writeBuf);
			written += w;
			if(w == 0)
				try{
					Thread.sleep(1);
				}catch(InterruptedException e){
					throw new RuntimeException(e);
				}
		}
		return written;
	}
}
