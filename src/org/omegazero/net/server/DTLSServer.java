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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.common.NetCommon;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.impl.TLSConnection;
import org.omegazero.net.socket.provider.DatagramChannelProvider;

/**
 * TLS server for UDP sockets (DTLS).
 */
public class DTLSServer extends UDPServer {

	private static final Logger logger = LoggerUtil.createLogger();


	private final SSLContext sslContext;

	private String[] supportedApplicationLayerProtocols = null;

	/**
	 * 
	 * @param sslContext The SSL context to be used by the server. The context must be initialized with protocol "DTLS"
	 * @see UDPServer#UDPServer(String, Collection, Consumer, long, int)
	 */
	public DTLSServer(Collection<Integer> ports, SSLContext sslContext) {
		super(ports);
		this.sslContext = sslContext;
	}

	/**
	 * 
	 * @param sslContext The SSL context to be used by the server. The context must be initialized with protocol "DTLS"
	 * @see UDPServer#UDPServer(String, Collection, Consumer, long, int)
	 */
	public DTLSServer(Collection<InetAddress> bindAddresses, Collection<Integer> ports, Consumer<Runnable> worker, long idleTimeout, int receiveBufferSize,
			SSLContext sslContext) {
		super(bindAddresses, ports, worker, idleTimeout, receiveBufferSize);
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
	protected ChannelConnection handleConnection(SelectionKey serverKey, SocketAddress remote) throws IOException {
		ChannelConnection conn = new TLSConnection(serverKey, new DatagramChannelProvider(remote, super::writeBacklogStarted), remote, this.sslContext, false,
				this.supportedApplicationLayerProtocols, null);

		conn.setOnError((e) -> {
			// see notes in TLSServer
			if(e instanceof SSLHandshakeException)
				logger.warn("TLS handshake failed (remote address=", conn.getRemoteAddress(), "): ", NetCommon.isPrintStackTraces() ? e : e.toString());
			else if(e instanceof SSLException)
				logger.warn("TLS error (remote address=", conn.getRemoteAddress(), "): ", NetCommon.isPrintStackTraces() ? e : e.toString());
			else if(e instanceof IOException)
				logger.warn("Socket Error (remote address=", conn.getRemoteAddress(), "): ", NetCommon.isPrintStackTraces() ? e : e.toString());
			else
				logger.error("Error in connection (remote address=", conn.getRemoteAddress(), "): ", e);
		});
		return conn;
	}
}
