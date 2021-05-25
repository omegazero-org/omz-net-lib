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
package org.omegazero.net.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.event.Tasks;
import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.InetConnectionSelector;
import org.omegazero.net.common.SyncWorker;
import org.omegazero.net.socket.InetConnection;

/**
 * TCP server implementation of an {@link InetServer} based on java.nio channels.
 */
public abstract class TCPServer extends InetConnectionSelector implements InetServer {

	private static final Logger logger = LoggerUtil.createLogger();


	private Consumer<InetConnection> onNewConnection;

	private List<ServerSocketChannel> serverSockets = new ArrayList<>();

	private long connectionTimeoutCheckInterval;


	protected final InetAddress bindAddress;
	protected final Collection<Integer> ports;
	protected final int backlog;
	protected final Consumer<Runnable> worker;

	private long idleTimeout;

	/**
	 * 
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public TCPServer(Collection<Integer> ports) {
		this(null, ports, 0, null, 0);
	}

	/**
	 * Constructs a new <code>TCPServer</code> instance.<br>
	 * <br>
	 * To initialize the server, {@link InetServer#init()} of this object must be called. After the call succeeded, the server will listen on the specified local address
	 * (<b>bindAddress</b>) on the given <b>ports</b> and {@link InetServer#serverLoop()} must be called to start processing incoming connection requests and data.
	 * 
	 * @param bindAddress The local address to bind to (see {@link ServerSocketChannel#bind(java.net.SocketAddress, int)})
	 * @param ports       The list of ports to listen on
	 * @param backlog     The maximum number of pending connections (see {@link ServerSocketChannel#bind(java.net.SocketAddress, int)}). May be 0 to use a default value
	 * @param worker      A callback accepting tasks to run that may require increased processing time. May be <code>null</code> to run everything using a single thread
	 * @param idleTimeout The time in milliseconds to keep connections that had no traffic. May be 0 to disable closing idle connections
	 */
	public TCPServer(String bindAddress, Collection<Integer> ports, int backlog, Consumer<Runnable> worker, long idleTimeout) {
		if(bindAddress != null)
			try{
				this.bindAddress = InetAddress.getByName(bindAddress);
			}catch(Exception e){
				throw new IllegalArgumentException("bindAddress is invalid", e);
			}
		else
			this.bindAddress = null;
		this.ports = Objects.requireNonNull(ports, "ports must not be null");
		this.backlog = backlog;
		if(worker != null)
			this.worker = worker;
		else
			this.worker = new SyncWorker();

		this.idleTimeout = idleTimeout;
	}


	private void listen() throws IOException {
		super.initSelector();
		for(int p : this.ports){
			this.listenOnPort(p);
			logger.info("Listening plain on port " + p);
		}
	}

	private void listenOnPort(int port) throws IOException {
		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssc.bind(new InetSocketAddress(this.bindAddress, port), this.backlog);
		ssc.configureBlocking(false);
		ssc.register(super.selector, SelectionKey.OP_ACCEPT);
		this.serverSockets.add(ssc);
	}


	protected abstract InetConnection handleConnection(SocketChannel socketChannel) throws IOException;

	protected void handleConnectionPost(InetConnection connection) {
	}


	@Override
	public void init() throws IOException {
		this.listen();

		this.connectionTimeoutCheckInterval = Tasks.interval((args) -> {
			// still need to check for idle timeout even if it wasn't configured because it may change during runtime
			long timeout = TCPServer.this.idleTimeout;
			if(timeout <= 0)
				return;
			long currentTime = System.currentTimeMillis();
			try{
				for(SelectionKey k : super.selector.keys()){
					if(k.attachment() instanceof InetConnection){
						InetConnection conn = (InetConnection) k.attachment();
						long delta = currentTime - conn.getLastIOTime();
						if(delta < 0 || delta > timeout){
							logger.debug("Idle Timeout: ", conn.getRemoteAddress(), " (", delta, "ms)");
							conn.close();
						}
					}
				}
			}catch(Exception e){
				logger.warn("Error while checking idle timeouts: ", e.toString());
			}
		}, 5000).getId();
	}

	@Override
	public void close() throws IOException {
		Tasks.clear(this.connectionTimeoutCheckInterval);
		for(ServerSocketChannel ssc : this.serverSockets){
			ssc.close();
		}
		super.closeSelector();
	}

	@Override
	public void setConnectionCallback(Consumer<InetConnection> handler) {
		this.onNewConnection = handler;
	}

	@Override
	public void run() throws IOException {
		super.selectorLoop();
	}


	@Override
	protected void handleSelectedKey(SelectionKey key) throws IOException {
		if(key.isAcceptable()){
			ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
			SocketChannel socketChannel = serverChannel.accept();

			InetConnection conn = this.handleConnection(socketChannel);
			conn.setOnLocalClose(super::onConnectionClosed);

			conn.setOnConnect(() -> {
				if(TCPServer.this.onNewConnection != null){
					TCPServer.this.onNewConnection.accept(conn);
				}else
					throw new IllegalStateException("No connection handler is set");
			});

			this.handleConnectionPost(conn);

			// no need to call super.startRegister because we're in handleSelectedKey (not in a select call)
			socketChannel.register(super.selector, SelectionKey.OP_READ).attach(conn);
		}else if(key.isReadable() && key.attachment() instanceof InetConnection){
			InetConnection conn = (InetConnection) key.attachment();
			byte[] data = conn.read();
			if(data != null){
				this.worker.accept(() -> {
					conn.handleData(data);
				});
			}
		}
	}


	public long getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public InetAddress getBindAddress() {
		return bindAddress;
	}

	public int getBacklog() {
		return backlog;
	}
}
