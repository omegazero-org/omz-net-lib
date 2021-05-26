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
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.client.params.InetConnectionParameters;
import org.omegazero.net.common.InetConnectionSelector;
import org.omegazero.net.common.SyncWorker;
import org.omegazero.net.socket.InetConnection;

public abstract class TCPClientManager extends InetConnectionSelector implements InetClientManager {

	private static final Logger logger = LoggerUtil.createLogger();


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


	protected abstract InetConnection createConnection(SocketChannel socketChannel, InetConnectionParameters params) throws IOException;

	/**
	 * Called when a connection was established.
	 * 
	 * @param conn The {@link InetConnection} object representing the connection that was established
	 */
	protected abstract void handleConnect(InetConnection conn) throws IOException;


	@Override
	public void init() throws IOException {
		super.initSelector();
	}

	@Override
	public void close() throws IOException {
		super.closeSelector();
	}

	@Override
	public InetConnection connection(InetConnectionParameters params) throws IOException {
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);

		InetConnection conn = this.createConnection(socketChannel, params);
		conn.setOnLocalClose(super::onConnectionClosed);

		conn.setOnError((e) -> {
			logger.warn("Socket Error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
		});

		if(params.getLocal() != null)
			socketChannel.bind(params.getLocal());

		super.startRegister();
		socketChannel.register(super.selector, SelectionKey.OP_CONNECT).attach(conn);
		super.endRegister();
		return conn;
	}

	@Override
	public void run() throws IOException {
		super.selectorLoop();
	}


	@Override
	protected void handleSelectedKey(SelectionKey key) throws IOException {
		Objects.requireNonNull(key.attachment(), "SelectionKey attachment is null");
		if(!(key.attachment() instanceof InetConnection))
			throw new RuntimeException(
					"SelectionKey attachment is of type " + key.attachment().getClass().getName() + ", but expected type " + InetConnection.class.getName());
		InetConnection conn = (InetConnection) key.attachment();
		if(key.isConnectable()){
			SocketChannel channel = (SocketChannel) key.channel();
			try{
				if(channel.finishConnect()){
					key.interestOps(SelectionKey.OP_READ);
					this.handleConnect(conn);
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
		}
	}
}
