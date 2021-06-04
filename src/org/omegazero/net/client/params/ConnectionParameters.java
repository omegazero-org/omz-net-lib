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

public class ConnectionParameters {

	private final SocketAddress remote;
	private final SocketAddress local;

	public ConnectionParameters(SocketAddress remote) {
		this(remote, null);
	}

	public ConnectionParameters(SocketAddress remote, SocketAddress local) {
		this.remote = remote;
		this.local = local;
	}


	public SocketAddress getRemote() {
		return remote;
	}

	public SocketAddress getLocal() {
		return local;
	}
}
