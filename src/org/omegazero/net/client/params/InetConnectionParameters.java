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
import java.net.UnknownHostException;

public class InetConnectionParameters {

	private final InetSocketAddress remote;
	private final InetSocketAddress local;

	public InetConnectionParameters(InetSocketAddress remote) {
		this(remote, null);
	}

	public InetConnectionParameters(InetSocketAddress remote, InetSocketAddress local) {
		this.remote = remote;
		this.local = local;
	}

	public InetConnectionParameters(InetAddress remoteAddress, int remotePort) {
		this(new InetSocketAddress(remoteAddress, remotePort), null);
	}

	public InetConnectionParameters(String remoteAddress, int remotePort) {
		try{
			this.remote = new InetSocketAddress(InetAddress.getByName(remoteAddress), remotePort);
		}catch(UnknownHostException e){
			throw new RuntimeException("Invalid remoteAddress", e);
		}
		this.local = null;
	}


	public InetSocketAddress getRemote() {
		return remote;
	}

	public InetSocketAddress getLocal() {
		return local;
	}
}
