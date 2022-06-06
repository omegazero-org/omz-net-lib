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
package org.omegazero.net.nio.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

import org.omegazero.net.client.params.ConnectionParameters;
import org.omegazero.net.nio.socket.ChannelConnection;
import org.omegazero.net.nio.socket.PlainConnection;
import org.omegazero.net.nio.socket.provider.DatagramChannelProvider;

/**
 * {@link UDPClientManager} implementation for plaintext TCP sockets.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.client}.
 */
public class PlainUDPClientManager extends UDPClientManager {

	public PlainUDPClientManager() {
		super();
	}

	public PlainUDPClientManager(Consumer<Runnable> worker) {
		super(worker);
	}


	@Override
	protected ChannelConnection createConnection(SelectionKey selectionKey, ConnectionParameters params) throws IOException {
		return new PlainConnection(selectionKey, new DatagramChannelProvider(), params.getRemote());
	}

	@Override
	protected void handleConnect(ChannelConnection conn) {
		conn.handleConnect();
	}
}
