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
import java.util.LinkedList;
import java.util.function.Consumer;

import org.omegazero.net.socket.ChannelConnection;

public class DatagramChannelProvider implements ChannelProvider {

	private final SocketAddress remote;
	private final Consumer<ChannelConnection> notifyWriteBacklog;

	private ChannelConnection connection;
	private SelectionKey selectionKey;

	private DatagramChannel socket;

	private Deque<byte[]> readBacklog = new LinkedList<>();

	public DatagramChannelProvider() { // for outgoing requests; remote is not needed because socket is connected and notifyWriteBacklog is only for server channels
		this(null, null);
	}

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
			this.socket.close();
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
		if(this.notifyWriteBacklog != null) // this is a server datagram channel and we shouldnt edit the selection key
			this.notifyWriteBacklog.accept(this.connection);

		int ops = this.selectionKey.interestOps();
		if((ops & SelectionKey.OP_WRITE) == 0){
			this.selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
			this.selectionKey.selector().wakeup();
		}
	}

	@Override
	public void writeBacklogEnded() {
		if(this.notifyWriteBacklog == null)
			this.selectionKey.interestOps(this.selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
		// else the manager should notice itself that the backlog is flushed when flushWriteBacklog returns true and remove OP_WRITE
	}


	@Override
	public boolean isAvailable() {
		return true; // datagram channels can always send and receive data
	}
}
