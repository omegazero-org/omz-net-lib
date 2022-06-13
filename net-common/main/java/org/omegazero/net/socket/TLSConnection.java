/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.socket;

/**
 * Represents a {@link SocketConnection} encrypted using Transport Layer Security (TLS).
 * 
 * @since 2.1.0
 */
public interface TLSConnection extends SocketConnection {


	/**
	 * Returns {@code true} if the socket is connected and the TLS handshake has completed.
	 * 
	 * @return {@code true} if this socket is connected and the TLS handshake has completed
	 * @see #isSocketConnected()
	 */
	@Override
	public boolean isConnected();

	/**
	 * Returns {@code true} if the underlying socket is connected.
	 * 
	 * @return {@code true} if this socket is connected
	 * @see #isConnected()
	 */
	public boolean isSocketConnected();

	/**
	 * Returns the name of the protocol used for this connection.
	 * 
	 * @return The protocol name
	 */
	public String getProtocol();

	/**
	 * Returns the name of the TLS cipher suite used for this connection.
	 * 
	 * @return The TLS cipher name
	 */
	public String getCipher();

	/**
	 * Returns the application layer protocol name negotiated using a mechanism such as Application-Layer Protocol Negotiation (ALPN). If no such negotiation occurred or is not
	 * supported by the protocol or implementation, {@code null} is returned.
	 * 
	 * @return The application layer protocol name, or {@code null} if no negotiation occurred
	 */
	public String getApplicationProtocol();
}
