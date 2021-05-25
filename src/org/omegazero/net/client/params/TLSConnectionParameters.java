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

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class TLSConnectionParameters extends InetConnectionParameters {

	private String[] alpnNames;
	private String[] sniOptions;

	public TLSConnectionParameters(InetSocketAddress remote) {
		super(remote);
	}

	public TLSConnectionParameters(InetSocketAddress remote, InetSocketAddress local) {
		super(remote, local);
	}

	public TLSConnectionParameters(InetAddress remoteAddress, int remotePort) {
		super(remoteAddress, remotePort);
	}

	public TLSConnectionParameters(String remoteAddress, int remotePort) {
		super(remoteAddress, remotePort);
	}


	/**
	 * Sets the list of requested application layer protocol names to be negotiated using TLS ALPN (Application Layer Protocol Negotiation). The elements in this list should
	 * be ordered from most-preferred to least-preferred protocol name.<br>
	 * <br>
	 * If not set or <code>null</code> is passed, the first protocol name presented by the server is selected.
	 * 
	 * @param alpnNames The list of requested protocol names
	 */
	public void setAlpnNames(String[] alpnNames) {
		this.alpnNames = alpnNames;
	}

	public String[] getAlpnNames() {
		return this.alpnNames;
	}

	/**
	 * Sets the list of requested server names using TLS SNI (Server Name Indication).<br>
	 * <br>
	 * If not set or <code>null</code> is passed, SNI is disabled.
	 * 
	 * @param sniOptions The list of requested server names
	 */
	public void setSniOptions(String[] sniOptions) {
		this.sniOptions = sniOptions;
	}

	public String[] getSniOptions() {
		return sniOptions;
	}
}
