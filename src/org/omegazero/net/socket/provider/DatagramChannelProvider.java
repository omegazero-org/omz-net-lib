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
package org.omegazero.net.socket.provider;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import org.omegazero.net.socket.ChannelConnection;

public class DatagramChannelProvider implements ChannelProvider {

	private final SocketAddress remote;
	private final Consumer<ChannelConnection> notifyWriteBacklog;

	private ChannelConnection connection;
	private SelectionKey selectionKey;

	private DatagramChannel socket;

	private Deque<byte[]> readBacklog = new ConcurrentLinkedDeque<>();

	/**
	 * This constructor should be used if the datagram channel is used as a client which is about to be connected to a server.
	 */
	public DatagramChannelProvider() { // for outgoing requests; remote is not needed because socket is connected and notifyWriteBacklog is only for server channels
		this(null, null);
	}

	/**
	 * This constructor should be used if the datagram channel is used as a server.
	 * 
	 * @param remote             The address of the client
	 * @param notifyWriteBacklog Callback when the channel is experiencing write backlog
	 */
	public DatagramChannelProvider(SocketAddress remote, Consumer<ChannelConnection> notifyWriteBacklog) { // for incoming requests
		this.remote = remote;
		this.notifyWriteBacklog = notifyWriteBacklog;
	}


	public void addReadData(byte[] data) {
		this.readBacklog.add(data);
	}


	@Override
	public void init(ChannelConnection connection, SelectionKey key) {
		if(this.connection != null || this.selectionKey != null)
			throw new IllegalStateException("Already initialized");
		this.connection = connection;
		this.selectionKey = key;
		this.socket = (DatagramChannel) key.channel();
	}


	@Override
	public boolean connect(SocketAddress remote, int timeout) throws IOException {
		this.socket.connect(remote);
		return true;
	}

	@Override
	public void close() throws IOException {
		// only close this DatagramChannel if it was started as a client connection because otherwise the channel represents a server socket in which
		// case we shouldnt close it
		if(this.socket.isConnected()){
			synchronized(this.selectionKey){
				this.socket.close();
			}
		}
	}


	@Override
	public int read(ByteBuffer buf) throws IOException {
		if(this.socket.isConnected()){
			return this.socket.read(buf);
		}else{
			byte[] d = this.readBacklog.poll();
			if(d != null){
				buf.put(d);
				return d.length;
			}else
				return 0;
		}
	}

	@Override
	public int write(ByteBuffer buf) throws IOException {
		if(this.socket.isConnected()){
			return this.socket.write(buf);
		}else{
			if(this.remote == null)
				throw new IllegalStateException("Socket is not connected and no remote address was specified");
			return this.socket.send(buf, this.remote);
		}
	}


	@Override
	public void writeBacklogStarted() {
		if(this.notifyWriteBacklog != null)
			this.notifyWriteBacklog.accept(this.connection);

		this.enableOp(SelectionKey.OP_WRITE);
	}

	@Override
	public void writeBacklogEnded() {
		if(this.notifyWriteBacklog == null)
			this.enableOp(SelectionKey.OP_WRITE);
		// else the manager should notice itself that the backlog is flushed when flushWriteBacklog returns true and remove OP_WRITE
	}


	@Override
	public void setReadBlock(boolean block) {
		if(this.notifyWriteBacklog == null){
			if(block)
				this.disableOp(SelectionKey.OP_READ);
			else
				this.enableOp(SelectionKey.OP_READ);
		}
		// this is not supported for server channels
	}


	@Override
	public boolean isAvailable() {
		return true; // datagram channels can always send and receive data
	}


	private synchronized void enableOp(int op) {
		int ops = this.selectionKey.interestOps();
		if((ops & op) == 0){
			this.selectionKey.interestOps(ops | op);
			this.selectionKey.selector().wakeup();
		}
	}

	private synchronized void disableOp(int op) {
		this.selectionKey.interestOps(this.selectionKey.interestOps() & ~op);
	}
}
