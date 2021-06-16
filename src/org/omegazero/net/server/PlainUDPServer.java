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
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.impl.PlainConnection;
import org.omegazero.net.socket.provider.DatagramChannelProvider;

/**
 * {@link UDPServer} implementation for plaintext UDP sockets.
 */
public class PlainUDPServer extends UDPServer {

	private static final Logger logger = LoggerUtil.createLogger();


	/**
	 * 
	 * @see UDPServer#UDPServer(String, Collection, Consumer, long, int)
	 */
	public PlainUDPServer(Collection<Integer> ports) {
		super(ports);
	}

	/**
	 * 
	 * @see UDPServer#UDPServer(String, Collection, Consumer, long, int)
	 */
	public PlainUDPServer(Collection<InetAddress> bindAddresses, Collection<Integer> ports, Consumer<Runnable> worker, long idleTimeout, int receiveBufferSize) {
		super(bindAddresses, ports, worker, idleTimeout, receiveBufferSize);
	}


	@Override
	protected ChannelConnection handleConnection(SelectionKey serverKey, SocketAddress remote) throws IOException {
		ChannelConnection conn = new PlainConnection(serverKey, new DatagramChannelProvider(remote, super::writeBacklogStarted), remote);

		conn.setOnError((e) -> {
			if(e instanceof IOException)
				logger.warn("UDP Socket Error (remote address=", conn.getRemoteAddress(), "): ", NetCommon.isPrintStackTraces() ? e : e.toString());
			else
				logger.error("Error in UDP connection (remote address=", conn.getRemoteAddress(), "): ", e);
		});
		return conn;
	}

	@Override
	protected void handleConnectionPost(ChannelConnection connection) {
		connection.handleConnect();
	}
}
