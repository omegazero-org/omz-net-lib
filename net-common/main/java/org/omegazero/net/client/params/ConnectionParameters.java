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
package org.omegazero.net.client.params;

import java.net.SocketAddress;

/**
 * Contains parameters for creating {@linkplain org.omegazero.net.client.NetClientManager#connection(ConnectionParameters) outgoing connections}.
 */
public class ConnectionParameters {


	private final SocketAddress remote;
	private final SocketAddress local;


	/**
	 * Creates new {@link ConnectionParameters}.
	 * 
	 * @param remote The remote address
	 */
	public ConnectionParameters(SocketAddress remote) {
		this(remote, null);
	}

	/**
	 * Creates new {@link ConnectionParameters}.
	 * 
	 * @param remote The remote address
	 * @param local The local address to bind to
	 */
	public ConnectionParameters(SocketAddress remote, SocketAddress local) {
		this.remote = remote;
		this.local = local;
	}


	/**
	 * Returns the configured remote address.
	 * 
	 * @return The configured remote address
	 */
	public SocketAddress getRemote() {
		return this.remote;
	}

	/**
	 * Returns the configured local address.
	 * 
	 * @return The configured local address
	 */
	public SocketAddress getLocal() {
		return this.local;
	}
}
