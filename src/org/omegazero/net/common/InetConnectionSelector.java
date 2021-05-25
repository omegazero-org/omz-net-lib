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
package org.omegazero.net.common;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Iterator;

import org.omegazero.net.socket.InetConnection;

public abstract class InetConnectionSelector {

	protected Selector selector;

	// when a socket is closed locally, using SocketChannel.close(), the SelectionKey is removed from the Selector before the next time the select() method
	// returns, so there is no way to detect a socket closed locally except manually notifying and adding the connection to this list when close() is called
	// on the InetSocketConnection object. Alternative would be directly calling handleClose() in the close() method in eg InetSocketConnection, but
	// preferably the thread executing the selectorLoop() should emit all events and that likely wouldnt be the case because close() may be called by any thread.
	// this is ugly, but i dont think there is a better way
	private HashSet<InetConnection> closedConnections = new HashSet<>();


	private boolean running = false;

	private volatile boolean registerOperation = false;


	/**
	 * Called when a key was selected in a select call in the {@link InetConnectionSelector#selectorLoop()} method.
	 * 
	 * @param key The selected key
	 * @throws IOException
	 */
	protected abstract void handleSelectedKey(SelectionKey key) throws IOException;

	/**
	 * Called when a connection closed.
	 * 
	 * @param conn
	 * @throws IOException
	 */
	protected void handleConnectionClosed(InetConnection conn) throws IOException {
		conn.handleClose();
	}


	/**
	 * Notify this <code>InetConnectionSelector</code> that a connection was closed.<br>
	 * <br>
	 * This method is intended to be called by the subclass to notify this class that a connection has been closed locally, in contrast to the similarly named method
	 * {@link InetConnectionSelector#handleConnectionClosed(InetConnection)}, which is called by this class to notify the subclass that any connection has closed.
	 * 
	 * @param conn The connection that was closed
	 */
	protected void onConnectionClosed(InetConnection conn) {
		this.closedConnections.add(conn);
		this.selector.wakeup();
	}


	/**
	 * Initializes the {@link Selector} and sets this instance as running.<br>
	 * <br>
	 * After this method has been called successfully, {@link InetConnectionSelector#selectorLoop()} should be called to start performing IO operations.
	 * 
	 * @throws IOException
	 */
	protected void initSelector() throws IOException {
		this.selector = Selector.open();
		this.running = true;
	}

	/**
	 * Stops this instance and closes the {@link Selector} instance.
	 * 
	 * @throws IOException
	 */
	protected synchronized void closeSelector() throws IOException {
		if(!this.running)
			return;
		this.running = false;
		this.selector.close();
	}


	protected void startRegister() {
		this.registerOperation = true;
		this.selector.wakeup();
	}

	protected void endRegister() {
		this.registerOperation = false;
	}


	/**
	 * Runs the loop that continuously selects channels using the {@link Selector}.<br>
	 * <br>
	 * Will not return until {@link InetConnectionSelector#closeSelector()} is called. If {@link InetConnectionSelector#initSelector()} was never called, this method returns
	 * immediately.
	 * 
	 * @throws IOException
	 */
	protected void selectorLoop() throws IOException {
		while(this.running){
			if(this.closedConnections.size() > 0){
				Iterator<InetConnection> closeIterator = this.closedConnections.iterator();
				while(closeIterator.hasNext()){
					InetConnection conn = closeIterator.next();
					this.handleConnectionClosed(conn);
					closeIterator.remove();
				}
			}
			if(this.selector.select() != 0){
				synchronized(this){
					if(!this.selector.isOpen())
						continue;
					Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
					while(iterator.hasNext()){
						SelectionKey key = iterator.next();
						this.handleSelectedKey(key);
						iterator.remove();
					}
				}
			}
			if(this.registerOperation){
				long start = System.currentTimeMillis();
				while(this.registerOperation){
					if(System.currentTimeMillis() - start > 1000)
						throw new RuntimeException("Waiting time for register operation exceeded");
				}
			}
		}
	}


	public boolean isRunning() {
		return running;
	}
}
