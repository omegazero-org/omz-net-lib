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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.omegazero.common.event.Tasks;
import org.omegazero.net.socket.ChannelConnection;

public class SocketChannelProvider implements ChannelProvider {

	private ChannelConnection connection;
	private SelectionKey selectionKey;

	private SocketChannel socket;

	private long connectTimeout = -1;


	@Override
	public void init(ChannelConnection connection, SelectionKey key) {
		if(this.connection != null || this.selectionKey != null)
			throw new IllegalStateException("Already initialized");
		this.connection = connection;
		this.selectionKey = key;
		this.socket = (SocketChannel) key.channel();
	}


	@Override
	public boolean connect(SocketAddress remote, int timeout) throws IOException {
		boolean imm = this.socket.connect(remote);
		if(!imm){
			this.selectionKey.interestOps(SelectionKey.OP_CONNECT);
			this.selectionKey.selector().wakeup();
			if(timeout > 0)
				this.connectTimeout = Tasks.timeout((args) -> {
					if(!SocketChannelProvider.this.connection.hasConnected()){
						SocketChannelProvider.this.connection.handleTimeout();
						SocketChannelProvider.this.connection.close();
					}
				}, timeout).daemon().getId();
		}
		return imm;
	}

	@Override
	public void close() throws IOException {
		if(this.connectTimeout >= 0)
			Tasks.clear(this.connectTimeout);
		this.socket.close();
	}


	@Override
	public int read(ByteBuffer buf) throws IOException {
		return this.socket.read(buf);
	}

	@Override
	public int write(ByteBuffer buf) throws IOException {
		return this.socket.write(buf);
	}


	@Override
	public void writeBacklogStarted() {
		int ops = this.selectionKey.interestOps();
		if((ops & SelectionKey.OP_WRITE) == 0){
			this.selectionKey.interestOps(ops | SelectionKey.OP_WRITE);
			this.selectionKey.selector().wakeup();
		}
	}

	@Override
	public void writeBacklogEnded() {
		this.selectionKey.interestOps(this.selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
	}


	@Override
	public boolean isAvailable() {
		return this.socket.isConnected();
	}
}
