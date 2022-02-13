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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.event.Tasks;
import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.ConnectionSelectorHandler;
import org.omegazero.net.common.SyncWorker;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.SocketConnection;
import org.omegazero.net.socket.provider.DatagramChannelProvider;

/**
 * UDP/IP server implementation of a {@link NetServer} based on java.nio channels.
 */
public abstract class UDPServer extends ConnectionSelectorHandler implements NetServer {

	private static final Logger logger = LoggerUtil.createLogger();


	private Consumer<SocketConnection> onNewConnection;

	private List<DatagramChannel> serverChannels = new ArrayList<>();

	private long connectionTimeoutCheckInterval;


	protected final Collection<InetAddress> bindAddresses;
	protected final Collection<Integer> ports;
	protected final Consumer<Runnable> worker;
	private final ByteBuffer receiveBuffer;

	private long idleTimeout;

	private final Map<SocketAddress, ChannelConnection> connections = new HashMap<>();
	private final List<ChannelConnection> backloggedConnections = new LinkedList<>();

	/**
	 * 
	 * @see UDPServer#UDPServer(String, Collection, Consumer, long, int)
	 */
	public UDPServer(Collection<Integer> ports) {
		this(null, ports, null, 60000, 8192);
	}

	/**
	 * Constructs a new <code>UDPServer</code> instance.<br>
	 * <br>
	 * To initialize the server, {@link NetServer#init()} of this object must be called. After the call succeeded, the server will listen on the specified local address
	 * (<b>bindAddress</b>) on the given <b>ports</b> and {@link NetServer#start()} must be called to start processing incoming and outgoing data.
	 * 
	 * @param bindAddresses     A collection of local addresses to bind to (see {@link ServerSocketChannel#bind(java.net.SocketAddress, int)}). May be <code>null</code> to use
	 *                          an automatically assigned address
	 * @param ports             The list of ports to listen on
	 * @param worker            A callback accepting tasks to run that may require increased processing time. May be <code>null</code> to run everything using a single thread
	 * @param idleTimeout       The time in milliseconds to keep connections that had no traffic. Metadata for UDP connections not closed using
	 *                          {@link SocketConnection#close()} or by this idle timeout will be stored indefinitely, which should be avoided
	 * @param receiveBufferSize The size of the receive buffer. Should be set to the maximum expected packet size
	 */
	public UDPServer(Collection<InetAddress> bindAddresses, Collection<Integer> ports, Consumer<Runnable> worker, long idleTimeout, int receiveBufferSize) {
		if(bindAddresses != null)
			this.bindAddresses = bindAddresses;
		else
			this.bindAddresses = Arrays.asList((InetAddress) null);
		this.ports = Objects.requireNonNull(ports, "ports must not be null");
		if(worker != null)
			this.worker = worker;
		else
			this.worker = new SyncWorker();
		this.receiveBuffer = ByteBuffer.allocate(receiveBufferSize + 1);

		this.setIdleTimeout(idleTimeout);
	}


	private void listen() throws IOException {
		super.initSelector();
		for(int p : this.ports){
			this.listenOnPort(p);
		}
	}

	private void listenOnPort(int port) throws IOException {
		for(InetAddress bindAddress : this.bindAddresses){
			DatagramChannel dc = DatagramChannel.open();
			InetSocketAddress soaddr = new InetSocketAddress(bindAddress, port);
			dc.bind(soaddr);
			logger.info("Listening UDP on " + soaddr.getAddress().getHostAddress() + ":" + soaddr.getPort());
			dc.configureBlocking(false);
			super.registerChannel(dc, SelectionKey.OP_READ);
			this.serverChannels.add(dc);
		}
	}


	protected abstract ChannelConnection handleConnection(SelectionKey serverKey, SocketAddress remote) throws IOException;

	protected void handleConnectionPost(ChannelConnection connection) {
	}


	protected final void writeBacklogStarted(ChannelConnection connection) {
		synchronized(this.backloggedConnections){
			this.backloggedConnections.add(connection);
		}
	}


	@Override
	public void init() throws IOException {
		this.listen();

		this.connectionTimeoutCheckInterval = Tasks.interval((args) -> {
			long timeout = UDPServer.this.idleTimeout;
			long currentTime = System.currentTimeMillis();
			try{
				synchronized(UDPServer.this.connections){
					if(!UDPServer.this.connections.isEmpty()){
						List<SocketConnection> closeConns = new LinkedList<>();
						for(SocketConnection conn : UDPServer.this.connections.values()){
							long delta = currentTime - conn.getLastIOTime();
							if(delta < 0 || delta > timeout){
								logger.debug("Idle Timeout: ", conn.getRemoteAddress(), " (", delta, "ms)");
								closeConns.add(conn); // need to do this because onConnectionClosed is called by close() and it modifies the map
							}
						}
						for(SocketConnection conn : closeConns)
							conn.close();
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
		for(DatagramChannel dc : this.serverChannels){
			dc.close();
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
	protected void handleSelectedKey(SelectionKey key) throws IOException {
		if(key.isReadable()){
			ChannelConnection conn = this.incomingPacket(key);
			if(conn == null)
				return;

			byte[] data = conn.read();
			if(data != null){
				this.worker.accept(() -> {
					conn.handleData(data);
				});
			}
		}else if(key.isWritable()){
			boolean allFlushed = true;
			synchronized(this.backloggedConnections){
				java.util.Iterator<ChannelConnection> it = this.backloggedConnections.iterator();
				while(it.hasNext()){
					ChannelConnection conn = it.next();
					if(conn.flushWriteBacklog()){
						it.remove();
					}else
						allFlushed = false;
				}
			}
			if(allFlushed)
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
		}else
			throw new RuntimeException("Invalid key state: " + key.readyOps());
	}

	private ChannelConnection incomingPacket(SelectionKey key) throws IOException {
		DatagramChannel serverChannel = (DatagramChannel) key.channel();

		this.receiveBuffer.clear();
		SocketAddress remote = serverChannel.receive(this.receiveBuffer);
		if(remote == null){
			logger.warn("Received OP_READ but no datagram is available");
			return null;
		}
		if(!this.receiveBuffer.hasRemaining()){ // packet is very likely truncated, drop it
			logger.warn("Dropping too large incoming packet (>= ", this.receiveBuffer.capacity(), " bytes), set the receiveBufferSize to a larger value");
			return null;
		}
		this.receiveBuffer.flip();

		ChannelConnection conn;
		synchronized(this.connections){
			conn = this.connections.get(remote);
			if(conn == null){
				ChannelConnection newConn = this.handleConnection(key, remote);

				newConn.setOnLocalClose(this::onConnectionClosed);

				newConn.setOnConnect(() -> {
					if(UDPServer.this.onNewConnection != null){
						UDPServer.this.onNewConnection.accept(newConn);
					}else
						throw new IllegalStateException("No connection handler is set");
				});

				this.handleConnectionPost(newConn);

				conn = newConn;
				this.connections.put(remote, conn);
			}
		}

		DatagramChannelProvider provider = (DatagramChannelProvider) conn.getProvider();
		byte[] rdata = new byte[this.receiveBuffer.remaining()];
		this.receiveBuffer.get(rdata);
		provider.addReadData(rdata);
		return conn;
	}


	@Override
	protected void onConnectionClosed(SocketConnection conn) {
		synchronized(this.connections){
			if(!this.connections.remove(conn.getRemoteAddress(), conn))
				logger.warn("Closed nonexistent connection to ", conn.getRemoteAddress());
		}
		super.onConnectionClosed(conn);
	}


	public long getIdleTimeout() {
		return this.idleTimeout;
	}

	public void setIdleTimeout(long idleTimeout) {
		if(idleTimeout <= 0)
			throw new IllegalArgumentException("idleTimeout must be a positive integer");
		this.idleTimeout = idleTimeout;
	}
}
