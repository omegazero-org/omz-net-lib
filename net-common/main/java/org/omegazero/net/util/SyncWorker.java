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
package org.omegazero.net.util;

import java.util.function.Consumer;

/**
 * A {@code Runnable} consumer that executes given {@code Runnable}s synchronously.
 */
public class SyncWorker implements Consumer<Runnable> {


	/**
	 * {@linkplain Runnable#run() Runs} the given {@code Runnable}.
	 * 
	 * @param t The {@code Runnable}
	 */
	@Override
	public void accept(Runnable t) {
		t.run();
	}
}
