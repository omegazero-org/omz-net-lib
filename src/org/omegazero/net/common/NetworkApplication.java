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

public interface NetworkApplication {

	/**
	 * Initializes this application.
	 * 
	 * @throws IOException If an IO error occurs during initialization
	 */
	public void init() throws IOException;

	/**
	 * Closes this application, closing all bound and connected sockets and stopping the {@link NetworkApplication#loop}.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	public void close() throws IOException;

	/**
	 * Runs the main loop of this instance. This loop processes incoming or outgoing connection requests and network traffic.<br>
	 * <br>
	 * Under normal circumstances, should never return before {@link NetworkApplication#close()} is called, otherwise, the function should return as soon as possible.
	 * 
	 * @throws IOException If an IO error occurs during any networking operation
	 */
	public void run() throws IOException;
}
