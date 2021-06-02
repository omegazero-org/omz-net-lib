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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;

import org.omegazero.common.event.Tasks;

/**
 * Represents an {@link InetConnection} based on a java.nio {@link SocketChannel}.
 */
public abstract class InetSocketConnection extends InetConnection {


	private final SelectionKey selectionKey;

	private final SocketChannel socket;
	private final InetSocketAddress remoteAddress;
	private final InetSocketAddress localAddress;
	private long lastIOTime;

	protected ByteBuffer readBuf;
	protected ByteBuffer writeBuf;

	private Deque<byte[]> writeBacklog = new LinkedList<>();
	private ByteBuffer writeBufTemp;

	private long connectTimeout = -1;

	public InetSocketConnection(SelectionKey selectionKey) throws IOException {
		this(selectionKey, null);
	}

	public InetSocketConnection(SelectionKey selectionKey, InetSocketAddress remote) throws IOException {
		this.selectionKey = selectionKey;

		if(!(selectionKey.channel() instanceof SocketChannel))
			throw new IllegalArgumentException("The SelectionKey channel must be of type " + SocketChannel.class.getName());
		this.socket = (SocketChannel) selectionKey.channel();

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
			else{
				this.selectionKey.interestOps(SelectionKey.OP_CONNECT);
				this.selectionKey.selector().wakeup();
				if(timeout > 0)
					this.connectTimeout = Tasks.timeout((args) -> {
						if(!InetSocketConnection.this.hasConnected()){
							InetSocketConnection.this.handleTimeout();
							InetSocketConnection.this.close();
						}
					}, timeout).daemon().getId();
			}
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
	 * @return <code>true</code> if this socket is open and connected
	 */
	@Override
	public boolean isConnected() {
		return this.socket.isConnected() && this.socket.isOpen();
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


	/**
	 * Attempts to write any remaining data in the write buffer and write backlog to the socket and returns <code>true</code> if all data could be written.
	 * 
	 * @return <code>true</code> if all pending data was written to the socket
	 * @throws IOException
	 */
	public boolean flushWriteBacklog() {
		try{
			return this.flushWriteBacklog0();
		}catch(Exception e){
			this.handleError(e);
		}
		return false;
	}


	private void ensureNonBlocking() throws IOException {
		if(this.socket.isBlocking())
			this.socket.configureBlocking(false);
	}

	private int addToWriteBacklog() {
		synchronized(this.writeBuf){
			if(!this.writeBuf.hasRemaining())
				return 0;
			if(this.writeBufTemp == null){
				this.writeBufTemp = ByteBuffer.allocate(this.writeBuf.capacity());
				this.writeBufTemp.flip(); // set to no remaining bytes
			}
			byte[] wb = new byte[this.writeBuf.remaining()];
			this.writeBuf.get(wb);
			this.writeBacklog.add(wb);

			int ops = this.selectionKey.interestOps();
			if((ops & SelectionKey.OP_WRITE) == 0){
				this.selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
				this.selectionKey.selector().wakeup();
			}
			return wb.length;
		}
	}

	private boolean flushWriteBacklog0() throws IOException {
		if(this.writeBufTemp == null) // writeBufTemp not even created, so never used write backlog
			return true;
		synchronized(this.writeBuf){ // sync with writeBuf (not temp) to prevent concurrent write attempts in writeToSocket
			if(this.writeBufTemp.hasRemaining()){
				this.socket.write(this.writeBufTemp);
				if(this.writeBufTemp.hasRemaining()) // the socket still was not able to write the whole content
					return false;
			}
			while(this.writeBacklog.size() > 0){
				byte[] data = this.writeBacklog.remove();
				this.writeBufTemp.clear();
				this.writeBufTemp.put(data);
				this.writeBufTemp.flip();
				this.socket.write(this.writeBufTemp);
				if(this.writeBufTemp.hasRemaining()) // could not write entire data packet to socket
					break;
			}
			if(!this.writeBufTemp.hasRemaining()){
				// can remove write op because all data was written
				this.selectionKey.interestOps(this.selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
				return true;
			}else{
				return false;
			}
		}
	}


	protected final int readFromSocket() throws IOException {
		this.lastIOTime = System.currentTimeMillis();
		return this.socket.read(this.readBuf);
	}

	protected final int writeToSocket() throws IOException {
		synchronized(this.writeBuf){
			long start = System.currentTimeMillis();
			this.lastIOTime = start;
			if(this.writeBacklog.size() > 0 || (this.writeBufTemp != null && this.writeBufTemp.hasRemaining())){
				// there is still data in the write backlog, so dont even attempt to write to socket because it is full
				return this.addToWriteBacklog();
			}
			int written = 0;
			while(this.writeBuf.hasRemaining()){
				int w = this.socket.write(this.writeBuf);
				if(w == 0){
					w = this.addToWriteBacklog();
					if(this.writeBuf.hasRemaining())
						throw new IllegalStateException("writeBuf has data after addToWriteBacklog()");
				}
				written += w;
			}
			return written;
		}
	}
}
