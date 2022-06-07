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
import java.io.UncheckedIOException;

/**
 * Represents any networking application (for example, a client or server).
 */
public interface NetworkApplication extends Runnable {

	/**
	 * Initializes this application.
	 * 
	 * @throws IOException If an IO error occurs during initialization
	 */
	public void init() throws IOException;

	/**
	 * Closes this application, closing all bound and connected sockets and stopping the main loop, causing a call to {@link #start} to return.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	public void close() throws IOException;

	/**
	 * Runs the main loop of this instance. This loop processes incoming or outgoing connection requests and network traffic.
	 * <p>
	 * Under normal circumstances, never returns before {@link #close()} is called. After {@code close()} is called, this function should return as soon as possible.
	 * <p>
	 * If this method is called before {@link #init()}, the behavior is undefined.
	 * 
	 * @throws IOException If a fatal IO error occurs during any networking operation
	 */
	public void start() throws IOException;


	/**
	 * Method which implements the {@link Runnable#run()} function.
	 * <p>
	 * This method is equivalent to {@link #start()}, except that any <code>IOException</code>s thrown are wrapped into an <code>UncheckedIOException</code>. An application using
	 * this instance should use {@link #start()} instead, to properly handle errors.
	 */
	@Override
	public default void run() {
		try{
			this.start();
		}catch(IOException e){
			throw new UncheckedIOException("Error in " + this.getClass().getName() + " main loop", e);
		}
	}
}
