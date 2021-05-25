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
package org.omegazero.net.client;

import java.io.IOException;

import org.omegazero.net.client.params.InetConnectionParameters;
import org.omegazero.net.common.NetworkApplication;
import org.omegazero.net.socket.InetConnection;

public interface InetClientManager extends NetworkApplication {

	/**
	 * Creates a new connection instance based on the given parameters to be managed by this <code>InetClientManager</code>.<br>
	 * <br>
	 * {@link InetConnection#connect()} will need to be called on the returned connection instance to initiate the connection.
	 * 
	 * @param params Parameters for this connection
	 * @return The new connection instance
	 * @throws IOException
	 */
	public InetConnection connection(InetConnectionParameters params) throws IOException;
}
