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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.socket.InetConnection;
import org.omegazero.net.socket.impl.TLSConnection;

/**
 * {@link TCPServer} implementation for sockets encrypted using SSL or TLS.
 */
public class TLSServer extends TCPServer {

	private static final Logger logger = LoggerUtil.createLogger();

	private final SSLContext sslContext;

	private String[] supportedApplicationLayerProtocols = null;

	/**
	 * 
	 * @param sslContext The SSL context to be used by the server
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public TLSServer(Collection<Integer> ports, SSLContext sslContext) {
		super(ports);
		this.sslContext = sslContext;
	}

	/**
	 * 
	 * @param sslContext The SSL context to be used by the server
	 * @see TCPServer#TCPServer(String, Collection, int, Consumer, long)
	 */
	public TLSServer(String bindAddress, Collection<Integer> ports, int backlog, Consumer<Runnable> worker, long idleTimeout, SSLContext sslContext) {
		super(bindAddress, ports, backlog, worker, idleTimeout);
		this.sslContext = sslContext;
	}


	/**
	 * Sets the list of supported application layer protocol names to be negotiated using TLS ALPN (Application Layer Protocol Negotiation). The elements in this list should
	 * be ordered from most-preferred to least-preferred protocol name.<br>
	 * <br>
	 * If not set or <code>null</code> is passed, the first protocol name presented by the client is selected. If the client does not request ALPN, this list is ignored.
	 * 
	 * @param supportedApplicationLayerProtocols The list of supported protocol names
	 */
	public void setSupportedApplicationLayerProtocols(String[] supportedApplicationLayerProtocols) {
		this.supportedApplicationLayerProtocols = supportedApplicationLayerProtocols;
	}

	/**
	 * 
	 * @return The list of configured supported application layer protocol names, or <code>null</code> of none were configured
	 */
	public String[] getSupportedApplicationLayerProtocols() {
		return supportedApplicationLayerProtocols;
	}


	@Override
	protected InetConnection handleConnection(SocketChannel socketChannel) throws IOException {
		InetConnection conn = new TLSConnection(socketChannel, this.sslContext, false, (r) -> {
			TLSServer.super.worker.accept(() -> {
				r.run();
			});
		}, this.supportedApplicationLayerProtocols);

		// see note in PlainTCPServer
		conn.setOnError((e) -> {
			// if it is a SSLHandshakeException, no need to be verbose because it isn't a fatal error (may just be caused because a client
			// is bad or doesn't accept a certificate)
			if(e instanceof SSLHandshakeException)
				logger.warn("TLS handshake failed (remote address=", conn.getRemoteAddress(), "): ", e.toString());
			else if(e instanceof SSLException) // same applies to SSLException, for example when a client sends a malformed SSL packet, a SSLException is thrown
				logger.warn("TLS error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
			else if(e instanceof IOException)
				logger.warn("Socket Error (remote address=", conn.getRemoteAddress(), "): ", e.toString());
			else
				logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", e);
		});
		return conn;
	}
}
