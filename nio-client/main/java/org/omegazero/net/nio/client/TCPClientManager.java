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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.client.NetClientManager;
import org.omegazero.net.client.params.ConnectionParameters;
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.nio.socket.ChannelConnection;
import org.omegazero.net.nio.util.ConnectionSelectorHandler;
import org.omegazero.net.socket.SocketConnection;

/**
 * TCP/IP implementation of a {@link NetClientManager}.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.client}.
 */
public abstract class TCPClientManager extends ConnectionSelectorHandler implements NetClientManager {

	private static final Logger logger = LoggerUtil.createLogger();


	// the connect() method returns true if the connection is established immediately, *instead* of firing OP_CONNECT. To have both cases look somewhat similar
	// (ie called by the selectorLoop() thread, asynchronously), this exists for similar reasons why closedConnections in ConnectionSelectorHandler exists. This is a mess, i know.
	private HashSet<SelectionKey> completedConnections = new HashSet<>();

	protected final Function<SocketConnection, Consumer<Runnable>> workerCreator;

	public TCPClientManager(Function<SocketConnection, Consumer<Runnable>> workerCreator) {
		this.workerCreator = workerCreator;
	}


	protected abstract ChannelConnection createConnection(SelectionKey selectionKey, ConnectionParameters params) throws IOException;

	/**
	 * Called when a connection was established.
	 * 
	 * @param conn The {@link ChannelConnection} object representing the connection that was established
	 */
	protected abstract void handleConnect(ChannelConnection conn);


	private void finishConnect(SelectionKey key) {
		synchronized(key){
			if(!key.isValid())
				return;
			key.interestOps(SelectionKey.OP_READ);
		}
		this.handleConnect((ChannelConnection) key.attachment());
	}


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
	public synchronized SocketConnection connection(ConnectionParameters params) throws IOException {
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);

		SelectionKey key = super.registerChannel(socketChannel, 0); // the connection instance must set OP_CONNECT if necessary, otherwise, OP_READ will be set in finishConnect
		ChannelConnection conn = this.createConnection(key, params);
		key.attach(conn);

		conn.setOnLocalClose(super::onConnectionClosed);

		if(this.workerCreator != null)
			conn.setWorker(this.workerCreator.apply(conn));

		conn.setOnError((e) -> {
			NetCommon.logSocketError(logger, "Socket Error", conn, e);
		});

		if(params.getLocal() != null)
			socketChannel.bind(params.getLocal());

		conn.setOnLocalConnect((c) -> {
			synchronized(TCPClientManager.this.completedConnections){
				TCPClientManager.this.completedConnections.add(key);
			}
			TCPClientManager.super.selectorWakeup();
		});
		return conn;
	}


	@Override
	protected void loopIteration() throws IOException {
		super.loopIteration();
		if(this.completedConnections.size() > 0){
			synchronized(this.completedConnections){
				Iterator<SelectionKey> compIterator = this.completedConnections.iterator();
				while(compIterator.hasNext()){
					logger.trace("Handling local connect");
					this.finishConnect(compIterator.next());
					compIterator.remove();
				}
			}
		}
	}

	@Override
	protected synchronized void handleSelectedKey(SelectionKey key) throws IOException {
		Objects.requireNonNull(key.attachment(), "SelectionKey attachment is null");
		ChannelConnection conn = (ChannelConnection) key.attachment();
		if(key.isConnectable()){
			SocketChannel channel = (SocketChannel) key.channel();
			try{
				if(channel.finishConnect()){
					this.finishConnect(key);
				}else
					throw new IOException("Socket channel was marked as connectable but finishConnect returned false");
			}catch(IOException e){
				conn.handleError(e);
			}
		}else if(key.isReadable()){
			byte[] data = conn.read();
			if(data != null){
				conn.handleData(data);
			}
		}else if(key.isWritable()){
			conn.flushWriteBacklog();
		}else
			throw new RuntimeException("Invalid key state: " + key.readyOps());
	}
}
