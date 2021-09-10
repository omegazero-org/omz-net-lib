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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.omegazero.common.event.Tasks;
import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.ConnectionSelectorHandler;
import org.omegazero.net.common.SyncWorker;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.SocketConnection;

/**
 * TCP/IP server implementation of a {@link NetServer} based on java.nio channels.
 */
public abstract class TCPServer extends ConnectionSelectorHandler implements NetServer {

	private static final Logger logger = LoggerUtil.createLogger();


	private Consumer<SocketConnection> onNewConnection;

	private List<ServerSocketChannel> serverSockets = new ArrayList<>();

	private long connectionTimeoutCheckInterval;


	protected final Collection<InetAddress> bindAddresses;
	protected final Collection<Integer> ports;
	protected final int backlog;
	protected final Consumer<Runnable> worker;

	private long idleTimeout;

	// only required for idle timeouts
	private final Set<ChannelConnection> connections = new java.util.HashSet<>();

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
	 * To initialize the server, {@link NetServer#init()} of this object must be called. After the call succeeded, the server will listen on the specified local address
	 * (<b>bindAddress</b>) on the given <b>ports</b> and {@link NetServer#start()} must be called to start processing incoming connection requests and data.
	 * 
	 * @param bindAddresses A collection of local addresses to bind to (see {@link ServerSocketChannel#bind(java.net.SocketAddress, int)}). May be <code>null</code> to use an
	 *                      automatically assigned address
	 * @param ports         The list of ports to listen on
	 * @param backlog       The maximum number of pending connections (see {@link ServerSocketChannel#bind(java.net.SocketAddress, int)}). May be 0 to use a default value
	 * @param worker        A callback accepting tasks to run that may require increased processing time. May be <code>null</code> to run everything using a single thread
	 * @param idleTimeout   The time in milliseconds to keep connections that had no traffic. May be 0 to disable closing idle connections
	 */
	public TCPServer(Collection<InetAddress> bindAddresses, Collection<Integer> ports, int backlog, Consumer<Runnable> worker, long idleTimeout) {
		if(bindAddresses != null)
			this.bindAddresses = bindAddresses;
		else
			this.bindAddresses = Arrays.asList((InetAddress) null);
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
		}
	}

	private void listenOnPort(int port) throws IOException {
		for(InetAddress bindAddress : this.bindAddresses){
			ServerSocketChannel ssc = ServerSocketChannel.open();
			InetSocketAddress soaddr = new InetSocketAddress(bindAddress, port);
			ssc.bind(soaddr, this.backlog);
			logger.info("Listening TCP on " + soaddr.getAddress().getHostAddress() + ":" + soaddr.getPort());
			ssc.configureBlocking(false);
			super.registerChannel(ssc, SelectionKey.OP_ACCEPT);
			this.serverSockets.add(ssc);
		}
	}


	protected abstract ChannelConnection handleConnection(SelectionKey selectionKey) throws IOException;

	protected void handleConnectionPost(ChannelConnection connection) {
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
				synchronized(this.connections){
					// originally, this was just an iteration over the selector key set (which would not require the connections list),
					// but it is apparently impossible to properly synchronize the set, so this needs to be done instead
					for(ChannelConnection conn : this.connections){
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
	public void start() throws IOException {
		super.runSelectorLoop();
	}

	@Override
	public void setConnectionCallback(Consumer<SocketConnection> handler) {
		this.onNewConnection = handler;
	}


	@Override
	protected void handleConnectionClosed(SocketConnection conn) throws IOException {
		synchronized(this.connections){
			if(!this.connections.remove(conn))
				logger.warn("Closed connection to ", conn.getRemoteAddress(), " was not in connections list");
		}
		super.handleConnectionClosed(conn);
	}

	@Override
	protected void handleSelectedKey(SelectionKey key) throws IOException {
		if(key.isAcceptable()){
			ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
			SocketChannel socketChannel = serverChannel.accept();
			if(socketChannel == null){
				logger.warn("Received OP_ACCEPT but no socket is available");
				return;
			}
			socketChannel.configureBlocking(false);

			SelectionKey connkey = super.registerChannel(socketChannel, SelectionKey.OP_READ);
			ChannelConnection conn = this.handleConnection(connkey);
			connkey.attach(conn);

			conn.setOnLocalClose(super::onConnectionClosed);

			conn.setOnConnect(() -> {
				if(TCPServer.this.onNewConnection != null){
					TCPServer.this.onNewConnection.accept(conn);
				}else
					throw new IllegalStateException("No connection handler is set");
			});

			this.handleConnectionPost(conn);

			synchronized(this.connections){
				this.connections.add(conn);
			}
		}else if(key.isReadable()){
			ChannelConnection conn = (ChannelConnection) key.attachment();
			byte[] data = conn.read();
			if(data != null){
				this.worker.accept(() -> {
					conn.handleData(data);
				});
			}
		}else if(key.isWritable()){
			ChannelConnection conn = (ChannelConnection) key.attachment();
			conn.flushWriteBacklog();
		}else
			throw new RuntimeException("Invalid key state: " + key.readyOps());
	}


	public long getIdleTimeout() {
		return this.idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public int getBacklog() {
		return this.backlog;
	}
}
