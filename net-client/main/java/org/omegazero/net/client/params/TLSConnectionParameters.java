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
 * Extended {@link ConnectionParameters} for connections encrypted using TLS.
 */
public class TLSConnectionParameters extends ConnectionParameters {


	private String[] alpnNames;
	private String[] sniOptions;


	/**
	 * Creates new {@link TLSConnectionParameters}.
	 * 
	 * @param remote The remote address
	 */
	public TLSConnectionParameters(SocketAddress remote) {
		super(remote);
	}

	/**
	 * Creates new {@link TLSConnectionParameters}.
	 * 
	 * @param remote The remote address
	 * @param local The local address to bind to
	 */
	public TLSConnectionParameters(SocketAddress remote, SocketAddress local) {
		super(remote, local);
	}


	/**
	 * Sets the list of requested application layer protocol names to be negotiated using TLS ALPN (Application Layer Protocol Negotiation). The elements in this list should
	 * be ordered from most-preferred to least-preferred protocol name.
	 * 
	 * @param alpnNames The list of requested protocol names
	 * @see javax.net.ssl.SSLParameters#setApplicationProtocols(String[])
	 */
	public void setAlpnNames(String[] alpnNames) {
		this.alpnNames = alpnNames;
	}

	/**
	 * Returns the configured ALPN options using {@link #setAlpnNames(String[])}.
	 * 
	 * @return The configured ALPN options
	 */
	public String[] getAlpnNames() {
		return this.alpnNames;
	}

	/**
	 * Sets the list of requested server names using TLS SNI (Server Name Indication).
	 * <p>
	 * If not set or <code>null</code> is passed, SNI is disabled.
	 * 
	 * @param sniOptions The list of requested server names
	 */
	public void setSniOptions(String[] sniOptions) {
		this.sniOptions = sniOptions;
	}

	/**
	 * Returns the configured SNI options using {@link #setSniOptions(String[])}.
	 * 
	 * @return The configured SNI options
	 */
	public String[] getSniOptions() {
		return this.sniOptions;
	}
}
