/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.common;

/**
 * Wraps any {@code Throwable} in this unchecked exception, accessible using {@link #getCause()}.
 * <p>
 * Thrown by {@link org.omegazero.net.socket.SocketConnection} methods if no {@code onError} handler is set. Note that, usually, classes creating and managing a {@code SocketConnection} will set a
 * default error handler, which will cause the error handler to be called instead of this exception being thrown.
 * 
 * @since 1.6
 */
public class UnhandledException extends RuntimeException {

	private static final long serialVersionUID = 1L;


	/**
	 * Creates a new {@link UnhandledException} with the given {@code Throwable}.
	 * 
	 * @param e The {@code Throwable}
	 */
	public UnhandledException(Throwable e) {
		super(e);
	}


	/**
	 * If this {@link UnhandledException} wraps an {@code IOException}, this method throws it; otherwise, it does nothing.
	 * 
	 * @throws java.io.IOException The wrapped {@code IOException}
	 */
	public void rethrowIOException() throws java.io.IOException {
		Throwable e = super.getCause();
		if(e instanceof java.io.IOException)
			throw (java.io.IOException) e;
	}
}
