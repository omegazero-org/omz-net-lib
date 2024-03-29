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
package org.omegazero.net.nio.socket.provider;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.omegazero.common.event.Tasks;
import org.omegazero.net.nio.socket.ChannelConnection;

/**
 * A {@link ChannelProvider} for {@link SocketChannel}s.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.socket.provider}.
 */
public class SocketChannelProvider implements ChannelProvider {

	private ChannelConnection connection;
	private SelectionKey selectionKey;

	private SocketChannel socket;

	private Object connectTimeout;


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
				this.connectTimeout = Tasks.I.timeout((args) -> {
					if(!SocketChannelProvider.this.connection.hasConnected()){
						SocketChannelProvider.this.connection.handleTimeout();
						SocketChannelProvider.this.connection.destroy();
					}
				}, timeout).daemon();
		}
		return imm;
	}

	@Override
	public void close() throws IOException {
		Tasks.I.clear(this.connectTimeout);
		synchronized(this.selectionKey){
			this.socket.close();
		}
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
		this.enableOp(SelectionKey.OP_WRITE);
	}

	@Override
	public void writeBacklogEnded() {
		this.disableOp(SelectionKey.OP_WRITE);
	}


	@Override
	public void setReadBlock(boolean block) {
		if((this.selectionKey.interestOps() & SelectionKey.OP_CONNECT) != 0)
			return;
		if(block)
			this.disableOp(SelectionKey.OP_READ);
		else
			this.enableOp(SelectionKey.OP_READ);
	}


	@Override
	public boolean isAvailable() {
		return this.socket.isConnected();
	}


	@Override
	public SelectionKey getSelectionKey() {
		return this.selectionKey;
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
