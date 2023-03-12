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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.logging.LoggerUtil;
import org.omegazero.common.util.PropertyUtil;

/**
 * Wraps a java.nio {@link Selector}.
 * <p>
 * A thread must call {@link #initSelector()} followed by {@link #runSelectorLoop()} to start selecting keys, which can then be handled in the abstract method
 * {@link #handleSelectedKey(SelectionKey)}. Additional keys may be added safely while running using {@link #registerChannel(SelectableChannel, int, Object)}.
 * <p>
 * This class defines the following system properties:
 * <ul>
 * <li><code>org.omegazero.net.nioselector.rebuildThreshold</code> (integer, default 1024) - The maximum number of times the {@linkplain Selector#select() selection operation} may
 * return in a row without having any keys selected. If this threshold is exceeded, the selector will be rebuilt. This feature exists to mitigate the selector/epoll immediate
 * return bug, which may exist on older JVM distributions</li>
 * <li><code>org.omegazero.net.nioselector.maxRebuilds</code> (integer, default 8) - The maximum number of times the selector may be rebuilt in a row (see above). If this threshold
 * is exceeded, the {@linkplain #runSelectorLoop() selector loop} will exit with an <code>IOException</code></li>
 * </ul>
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.common}.
 */
public abstract class SelectorHandler {

	private static final Logger logger = LoggerUtil.createLogger();

	private static final int SELECTOR_REBUILD_THRESHOLD = PropertyUtil.getInt("org.omegazero.net.nioselector.rebuildThreshold", 1024);
	private static final int SELECTOR_REBUILDS_MAX = PropertyUtil.getInt("org.omegazero.net.nioselector.maxRebuilds", 8);

	private Selector selector;


	private boolean running = false;

	private volatile boolean registerOperation = false;


	/**
	 * Called when a key was selected in a {@linkplain Selector#select() select call} in the {@link #runSelectorLoop()} method.
	 * 
	 * @param key The selected key
	 * @throws IOException If an IO error occurs
	 */
	protected abstract void handleSelectedKey(SelectionKey key) throws IOException;

	/**
	 * Called every loop iteration in {@link #runSelectorLoop()}, which is every time {@link Selector#select()} returns, either because a {@link SelectionKey} was selected or the
	 * selector was woken up for any other reason (for example after a call to {@link #selectorWakeup()}).<br>
	 * <br>
	 * This method does nothing by default. Subclasses may override this method to perform additional required operations.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	protected void loopIteration() throws IOException {
	}


	/**
	 * Initializes the {@link Selector} and sets this instance as running.<br>
	 * <br>
	 * After this method has been called successfully, {@link SelectorHandler#selectorLoop()} should be called to start performing IO operations.
	 * 
	 * @throws IOException
	 */
	protected void initSelector() throws IOException {
		this.selector = Selector.open();
		this.running = true;
	}

	/**
	 * Stops the selector loop, closes all channels registered with this selector and closes the {@link Selector} instance.
	 * 
	 * @throws IOException
	 */
	protected synchronized void closeSelector() throws IOException {
		if(!this.running)
			return;
		this.running = false;
		for(SelectionKey key : this.selector.keys())
			key.channel().close();
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
	 * @param ops The interest set for the resulting key
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
	 * @param channel The channel to be registered
	 * @param ops The interest set for the resulting key
	 * @param attachment The attachment for the resulting key
	 * @return The resulting {@link SelectionKey}
	 * @throws IOException
	 * @see #registerChannel(SelectableChannel, int)
	 * @see SelectableChannel#register(Selector, int, Object)
	 */
	protected synchronized SelectionKey registerChannel(SelectableChannel channel, int ops, Object attachment) throws IOException {
		this.registerOperation = true;
		this.selector.wakeup();
		SelectionKey key = channel.register(this.selector, ops, attachment);
		this.registerOperation = false;
		return key;
	}


	private synchronized void rebuildSelector(boolean destroy) {
		try{
			Selector newSelector = Selector.open();
			Set<SelectionKey> keys = this.selectorKeys();
			for(SelectionKey key : keys){
				synchronized(key){
					if(!key.isValid())
						continue;
					SelectableChannel channel = key.channel();
					Object att = key.attachment();
					int ops = key.interestOps();
					key.cancel();
					if(!destroy)
						channel.register(newSelector, ops, att);
					else if(channel.isOpen())
						channel.close();
				}
			}
			this.selector.close();

			this.selector = newSelector;
		}catch(IOException e){
			logger.error("Error while rebuilding selector: ", e);
		}
	}


	/**
	 * Wakes up the selector, causing a {@linkplain #loopIteration() selection loop iteration}.
	 * 
	 * @see Selector#wakeup()
	 */
	protected void selectorWakeup() {
		this.selector.wakeup();
	}

	/**
	 * Returns the set of registered {@link SelectionKey}s. See {@link Selector#keys()}.
	 *
	 * @return The set of selection keys
	 */
	protected Set<SelectionKey> selectorKeys() {
		return this.selector.keys();
	}


	/**
	 * Runs the loop that continuously selects channels using the {@link Selector}.<br>
	 * <br>
	 * Will not return until {@link #closeSelector()} is called. If {@link #initSelector()} was never called, this method returns immediately.
	 * 
	 * @throws IOException If an IO error occurs, or if {@link #handleSelectedKey(SelectionKey)} or {@link #loopIteration()} throw an <code>IOException</code>
	 */
	protected void runSelectorLoop() throws IOException {
		int selectorSpins = 0;
		int selectorRebuilds = 0;
		while(this.running){
			this.loopIteration();
			// loopIteration may do anything including calling socket callbacks, which can in turn close this SelectorHandler
			// -> need to check again if running is true to not try and select on a closed selector
			if(!this.running)
				break;
			if(this.selector.select() != 0){
				synchronized(this){
					if(!this.selector.isOpen())
						continue;
					Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
					while(iterator.hasNext()){
						SelectionKey key = iterator.next();
						synchronized(key){
							if(key.isValid())
								this.handleSelectedKey(key);
						}
						iterator.remove();
					}
				}
				// reset these because everything seems fine
				selectorSpins = 0;
				selectorRebuilds = 0;
			}else
				selectorSpins++;
			if(this.registerOperation){
				long start = System.nanoTime();
				while(this.registerOperation){
					if(System.nanoTime() - start > 2000_000_000L)
						throw new RuntimeException("Waiting time for register operation exceeded");
				}
			}
			if(selectorSpins >= SELECTOR_REBUILD_THRESHOLD){
				selectorRebuilds++;
				if(SELECTOR_REBUILDS_MAX > 0 && selectorRebuilds > SELECTOR_REBUILDS_MAX)
					throw new IOException("Maximum selector rebuild count exceeded: " + selectorRebuilds);
				boolean destroy = selectorRebuilds == SELECTOR_REBUILDS_MAX;
				logger.warn("Selector.select() has returned prematurely ", selectorSpins, " times in a row, ", destroy ? "destroying channels" : "rebuilding",
						" (", selectorRebuilds, " of ", SELECTOR_REBUILDS_MAX, ")");
				this.rebuildSelector(destroy);
				selectorSpins = 0;
			}
		}
	}


	/**
	 * Returns {@code true} while this {@code SelectorHandler} is running; this is the case in the time between calls to {@link #initSelector()} and {@link #closeSelector()}.
	 * 
	 * @return {@code true} while this {@code SelectorHandler} is running
	 */
	public boolean isRunning() {
		return this.running;
	}
}
