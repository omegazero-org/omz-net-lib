package org.omegazero.net.common;

@FunctionalInterface
public interface ThrowingConsumer<T> {

	public void accept(T t) throws Exception;
}
