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
package org.omegazero.net.nio.server;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.nio.socket.ChannelConnection;
import org.omegazero.net.nio.socket.PlainConnection;
import org.omegazero.net.nio.socket.provider.SocketChannelProvider;

/**
 * {@link TCPServer} implementation for plaintext TCP sockets.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.server}.
 */
public class PlainTCPServer extends TCPServer {

	private static final Logger logger = LoggerUtil.createLogger();


	/**
	 * 
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public PlainTCPServer(Collection<Integer> ports) {
		super(ports);
	}

	/**
	 * 
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public PlainTCPServer(Collection<InetAddress> bindAddresses, Collection<Integer> ports, int backlog, Consumer<Runnable> worker, long idleTimeout) {
		super(bindAddresses, ports, backlog, worker, idleTimeout);
	}


	@Override
	protected ChannelConnection handleConnection(SelectionKey selectionKey) throws IOException {
		ChannelConnection conn = new PlainConnection(selectionKey, new SocketChannelProvider());

		// this is to handle errors that happen before another error handler was set, for example because a TLS handshake error occurred
		// and there was no way to set another error handler because the onConnect was not called yet
		// this may also be used as the default error handler by the application if no additional action is required other than terminating the connection
		conn.setOnError((e) -> {
			if(e instanceof IOException)
				NetCommon.logSocketError(logger, "Socket Error", conn, e);
			else
				logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", e);
		});
		return conn;
	}

	@Override
	protected void handleConnectionPost(ChannelConnection connection) {
		connection.handleConnect(); // plain connections have no additional handshake and are considered connected immediately
	}
}
