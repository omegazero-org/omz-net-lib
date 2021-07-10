package org.omegazero.net.common;

@FunctionalInterface
public interface ThrowingRunnable {

	public void run() throws Exception;
}
