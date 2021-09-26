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

import org.omegazero.common.logging.LogLevel;
import org.omegazero.common.logging.Logger;
import org.omegazero.common.util.PropertyUtil;
import org.omegazero.net.socket.SocketConnection;

public final class NetCommon {

	/**
	 * The version string of <i>omz-net-lib</i>.
	 */
	public static final String VERSION = "1.3";

	/**
	 * System property <code>org.omegazero.net.printStackTraces</code><br>
	 * <br>
	 * Whether the full stack trace of a socket error should be printed instead of just the error message.<br>
	 * <br>
	 * <b>Default:</b> <code>false</code>
	 */
	public static final boolean PRINT_STACK_TRACES = PropertyUtil.getBoolean("org.omegazero.net.printStackTraces", false);

	/**
	 * System property <code>org.omegazero.net.socketErrorDebug</code><br>
	 * <br>
	 * Whether the default socket error handler should print socket error messages with log level <i>DEBUG</i> instead of <i>WARN</i>. This may be used to reduce log noise on
	 * public-facing servers where there may be many misbehaving clients connecting to it.<br>
	 * <br>
	 * <b>Default:</b> <code>false</code>
	 */
	public static final boolean SOCKET_ERROR_DEBUG = PropertyUtil.getBoolean("org.omegazero.net.socketErrorDebug", false);


	/**
	 * @deprecated Access {@link NetCommon#PRINT_STACK_TRACES} directly instead
	 */
	@Deprecated
	public static boolean isPrintStackTraces() {
		return PRINT_STACK_TRACES;
	}

	public static void logSocketError(Logger logger, String msg, SocketConnection conn, Throwable e) {
		logger.log(SOCKET_ERROR_DEBUG ? LogLevel.DEBUG : LogLevel.WARN,
				new Object[] { msg, " (remote address=", conn.getRemoteAddress(), "): ", PRINT_STACK_TRACES ? e : e.toString() });
	}
}
