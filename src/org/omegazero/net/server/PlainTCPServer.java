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
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.socket.InetConnection;
import org.omegazero.net.socket.impl.PlainConnection;

/**
 * {@link TCPServer} implementation for plaintext sockets.
 */
public class PlainTCPServer extends TCPServer {

	private static final Logger logger = LoggerUtil.createLogger();

	/**
	 * 
	 * @see TCPServer#TCPServer(Collection)
	 */
	public PlainTCPServer(Collection<Integer> ports) {
		super(ports);
	}

	/**
	 * 
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public PlainTCPServer(String bindAddress, Collection<Integer> ports, int backlog, Consumer<Runnable> worker, long idleTimeout) {
		super(bindAddress, ports, backlog, worker, idleTimeout);
	}


	@Override
	protected InetConnection handleConnection(SocketChannel socketChannel) throws IOException {
		InetConnection conn = new PlainConnection(socketChannel, null);

		// this is to handle errors that happen before another error handler was set, for example because a TLS handshake error occurred
		// and there was no way to set another error handler because the onConnect was not called yet
		conn.setOnError((e) -> {
			if(e instanceof IOException)
				logger.warn("Socket Error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
			else
				logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", e);
		});
		return conn;
	}

	@Override
	protected void handleConnectionPost(InetConnection connection) {
		connection.handleConnect(); // plain connections have no additional handshake and are considered connected immediately
	}
}
