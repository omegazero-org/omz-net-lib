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

import org.omegazero.common.util.PropertyUtil;

public final class NetCommon {

	private static final boolean printStackTraces = PropertyUtil.getBoolean("org.omegazero.net.printStackTraces", false);


	public static boolean isPrintStackTraces() {
		return printStackTraces;
	}
}
