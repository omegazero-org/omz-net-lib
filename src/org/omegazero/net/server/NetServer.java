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

import java.util.function.Consumer;

import org.omegazero.net.common.NetworkApplication;
import org.omegazero.net.socket.SocketConnection;

public interface NetServer extends NetworkApplication {

	/**
	 * Sets the callback for a new incoming request.<br>
	 * <br>
	 * The first parameter of this callback is an {@link SocketConnection} instance representing the new connection from the client.
	 * 
	 * @param handler The connection callback
	 */
	public void setConnectionCallback(Consumer<SocketConnection> handler);
}
