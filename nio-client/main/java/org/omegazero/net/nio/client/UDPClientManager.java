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
package org.omegazero.net.nio.client;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.client.NetClientManager;
import org.omegazero.net.client.params.ConnectionParameters;
import org.omegazero.net.nio.socket.ChannelConnection;
import org.omegazero.net.nio.util.ConnectionSelectorHandler;
import org.omegazero.net.socket.SocketConnection;
import org.omegazero.net.util.SyncWorker;

/**
 * UDP/IP implementation of a {@link NetClientManager}.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.client}.
 */
public abstract class UDPClientManager extends ConnectionSelectorHandler implements NetClientManager {

	private static final Logger logger = LoggerUtil.createLogger();


	protected final Consumer<Runnable> worker;

	public UDPClientManager() {
		this(null);
	}

	public UDPClientManager(Consumer<Runnable> worker) {
		if(worker != null)
			this.worker = worker;
		else
			this.worker = new SyncWorker();
	}


	protected abstract ChannelConnection createConnection(SelectionKey selectionKey, ConnectionParameters params) throws IOException;

	/**
	 * Called after a connect request on a connection.
	 * 
	 * @param conn The {@link ChannelConnection} object on which connect was called
	 */
	protected abstract void handleConnect(ChannelConnection conn);


	@Override
	public void init() throws IOException {
		super.initSelector();
	}

	@Override
	public void close() throws IOException {
		super.closeSelector();
	}

	@Override
	public void start() throws IOException {
		super.runSelectorLoop();
	}

	@Override
	public SocketConnection connection(ConnectionParameters params) throws IOException {
		DatagramChannel channel = DatagramChannel.open();
		channel.configureBlocking(false);

		SelectionKey key = super.registerChannel(channel, 0);
		ChannelConnection conn = this.createConnection(key, params);
		key.attach(conn);

		conn.setOnLocalClose(super::onConnectionClosed);

		conn.setOnError((e) -> {
			logger.warn("UDP socket error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
		});

		if(params.getLocal() != null)
			channel.bind(params.getLocal());

		conn.setOnLocalConnect((c) -> {
			key.interestOps(SelectionKey.OP_READ); // only start listening for read when connect is called
			UDPClientManager.super.selectorWakeup();
			UDPClientManager.this.handleConnect(conn);
		});
		return conn;
	}


	@Override
	protected void handleSelectedKey(SelectionKey key) throws IOException {
		Objects.requireNonNull(key.attachment(), "SelectionKey attachment is null");
		ChannelConnection conn = (ChannelConnection) key.attachment();
		if(key.isReadable()){
			byte[] data = conn.read();
			if(data != null){
				this.worker.accept(() -> {
					conn.handleData(data);
				});
			}
		}else if(key.isWritable()){
			conn.flushWriteBacklog();
		}else
			throw new RuntimeException("Invalid key state: " + key.readyOps());
	}
}
