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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.common.util.PropertyUtil;

public abstract class SelectorHandler {

	private static final Logger logger = LoggerUtil.createLogger();

	private static final int SELECTOR_REBUILD_THRESHOLD = PropertyUtil.getInt("org.omegazero.net.nioselector.rebuildThreshold", 1024);
	private static final int SELECTOR_REBUILDS_MAX = PropertyUtil.getInt("org.omegazero.net.nioselector.maxRebuilds", 8);

	private Selector selector;


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


	/**
	 * See {@link #registerChannel(SelectableChannel, int, Object)}.<br>
	 * <br>
	 * An invocation of this method of the form
	 * 
	 * <pre>
	 * registerChannel(channel, ops)
	 * </pre>
	 * 
	 * is exactly the same as the invocation
	 * 
	 * <pre>
	 * {@link #registerChannel(SelectableChannel, int, Object) registerChannel}(channel, ops, null)
	 * </pre>
	 * 
	 * @param channel The channel to be registered
	 * @param ops     The interest set for the resulting key
	 * @return The resulting {@link SelectionKey}
	 * @throws IOException
	 */
	protected SelectionKey registerChannel(SelectableChannel channel, int ops) throws IOException {
		return this.registerChannel(channel, ops, null);
	}

	/**
	 * Registers the given channel with this <code>SelectorHandler</code>'s <code>Selector</code>. This function is safe to be called while another thread is in a selection
	 * operation in {@link #selectorLoop()}, in which case the selection operation will be interrupted to allow the new channel to be registered.
	 * 
	 * @param channel    The channel to be registered
	 * @param ops        The interest set for the resulting key
	 * @param attachment The attachment for the resulting key
	 * @return The resulting {@link SelectionKey}
	 * @throws IOException
	 * @see SelectableChannel#register(Selector, int, Object)
	 */
	protected synchronized SelectionKey registerChannel(SelectableChannel channel, int ops, Object attachment) throws IOException {
		this.registerOperation = true;
		this.selector.wakeup();
		SelectionKey key = channel.register(this.selector, ops, attachment);
		this.registerOperation = false;
		return key;
	}


	private synchronized void rebuildSelector() {
		try{
			Selector newSelector = Selector.open();
			Set<SelectionKey> keys = this.selectorKeys();
			for(SelectionKey key : keys){
				SelectableChannel channel = key.channel();
				Object att = key.attachment();
				int ops = key.interestOps();
				key.cancel();
				channel.register(newSelector, ops, att);
			}
			this.selector.close();

			this.selector = newSelector;
		}catch(IOException e){
			logger.warn("Error while rebuilding selector: ", e);
		}
	}


	protected void selectorWakeup() {
		this.selector.wakeup();
	}

	protected Set<SelectionKey> selectorKeys() {
		return this.selector.keys();
	}


	protected void loopIteration() throws IOException {
	}

	/**
	 * Runs the loop that continuously selects channels using the {@link Selector}.<br>
	 * <br>
	 * Will not return until {@link InetConnectionSelector#closeSelector()} is called. If {@link InetConnectionSelector#initSelector()} was never called, this method returns
	 * immediately.
	 * 
	 * @throws IOException
	 */
	protected void runSelectorLoop() throws IOException {
		int selectorSpins = 0;
		int selectorRebuilds = 0;
		while(this.running){
			this.loopIteration();
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
				selectorSpins = 0;
			}else
				selectorSpins++;
			if(this.registerOperation){
				long start = System.currentTimeMillis();
				while(this.registerOperation){
					if(System.currentTimeMillis() - start > 1000)
						throw new RuntimeException("Waiting time for register operation exceeded");
				}
			}
			if(selectorSpins >= SELECTOR_REBUILD_THRESHOLD){
				selectorRebuilds++;
				if(SELECTOR_REBUILDS_MAX > 0 && selectorRebuilds > SELECTOR_REBUILDS_MAX)
					throw new IOException("Maximum selector rebuild count exceeded: " + selectorRebuilds);
				logger.warn("Selector.select() has returned prematurely ", selectorSpins, " times in a row, rebuilding");
				this.rebuildSelector();
				selectorSpins = 0;
			}
		}
	}


	public boolean isRunning() {
		return running;
	}
}
