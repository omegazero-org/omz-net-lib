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
package org.omegazero.net.nio.util;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.net.nio.socket.ChannelConnection;
import org.omegazero.net.socket.AbstractSocketConnection;

/**
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.common}.
 */
public abstract class ConnectionSelectorHandler extends SelectorHandler {

	private static final Logger logger = LoggerUtil.createLogger();


	// when a socket is closed locally, using SocketChannel.close(), the SelectionKey is removed from the Selector before the next time the select() method
	// returns, so there is no way to detect a socket closed locally except manually notifying and adding the connection to this list when close() is called
	// on the ChannelConnection object. Alternative would be directly calling handleClose() in the close() method in ChannelConnection, but
	// preferably the thread executing the selectorLoop() should emit all events and that likely wouldnt be the case because close() may be called by any thread.
	// this is now the primary way of communicating closes, including when the peer closes the connection
	// subclasses must call setOnLocalClose(super::onConnectionClosed) on any ChannelConnection instance created
	private Collection<ChannelConnection> closedConnections = new ConcurrentLinkedQueue<>();


	/**
	 * Called when a connection closed.
	 * 
	 * @param conn The connection
	 * @throws IOException
	 */
	protected void handleConnectionClosed(AbstractSocketConnection conn) throws IOException {
		conn.handleClose();
	}


	/**
	 * Notify this <code>ConnectionSelectorHandler</code> that a {@code ChannelConnection} was closed locally by a call to {@link AbstractSocketConnection#close()}.
	 * 
	 * @param conn The connection that closed
	 */
	protected void onConnectionClosed(AbstractSocketConnection conn) {
		this.closedConnections.add((ChannelConnection) conn);
		super.selectorWakeup();
	}


	@Override
	protected synchronized void loopIteration() throws IOException {
		if(this.closedConnections.size() > 0){
			Iterator<ChannelConnection> closeIterator = this.closedConnections.iterator();
			while(closeIterator.hasNext()){
				logger.trace("Handling local close");
				ChannelConnection conn = closeIterator.next();
				closeIterator.remove();
				synchronized(conn.getProvider().getSelectionKey()){
					this.handleConnectionClosed(conn);
				}
			}
		}
	}
}
