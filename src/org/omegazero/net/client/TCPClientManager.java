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
package org.omegazero.net.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.client.params.InetConnectionParameters;
import org.omegazero.net.common.InetConnectionSelector;
import org.omegazero.net.common.SyncWorker;
import org.omegazero.net.socket.InetConnection;
import org.omegazero.net.socket.InetSocketConnection;

public abstract class TCPClientManager extends InetConnectionSelector implements InetClientManager {

	private static final Logger logger = LoggerUtil.createLogger();


	// the connect() method returns true if the connection is established immediately, *instead* of firing OP_CONNECT. To have both cases look somewhat similar
	// (ie called by the selectorLoop() thread, asynchronously), this exists for similar reasons why closedConnections in InetConnectionSelector exists. This is a mess, i know.
	private HashSet<SelectionKey> completedConnections = new HashSet<>();

	protected final Consumer<Runnable> worker;

	public TCPClientManager() {
		this(null);
	}

	public TCPClientManager(Consumer<Runnable> worker) {
		if(worker != null)
			this.worker = worker;
		else
			this.worker = new SyncWorker();
	}


	protected abstract InetSocketConnection createConnection(SelectionKey selectionKey, InetConnectionParameters params) throws IOException;

	/**
	 * Called when a connection was established.
	 * 
	 * @param conn The {@link InetSocketConnection} object representing the connection that was established
	 */
	protected abstract void handleConnect(InetSocketConnection conn) throws IOException;


	private void finishConnect(SelectionKey key) throws IOException {
		key.interestOps(SelectionKey.OP_READ);
		this.handleConnect((InetSocketConnection) key.attachment());
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
	public synchronized InetConnection connection(InetConnectionParameters params) throws IOException {
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);

		SelectionKey key = super.registerChannel(socketChannel, 0); // the connection instance must set OP_CONNECT if necessary, otherwise, OP_READ will be set in finishConnect
		InetSocketConnection conn = this.createConnection(key, params);
		key.attach(conn);

		conn.setOnLocalClose(super::onConnectionClosed);

		conn.setOnError((e) -> {
			logger.warn("Socket Error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
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
	public void run() throws IOException {
		super.runSelectorLoop();
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
		if(!(key.attachment() instanceof InetSocketConnection))
			throw new RuntimeException(
					"SelectionKey attachment is of type " + key.attachment().getClass().getName() + ", but expected type " + InetSocketConnection.class.getName());
		InetSocketConnection conn = (InetSocketConnection) key.attachment();
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
