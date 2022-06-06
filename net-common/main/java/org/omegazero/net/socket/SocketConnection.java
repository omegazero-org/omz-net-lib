/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.socket;

import java.net.SocketAddress;
import java.util.function.Consumer;

import org.omegazero.net.common.ThrowingConsumer;
import org.omegazero.net.common.ThrowingRunnable;

/**
 * Represents any type of connection between the local and a remote host.
 * <p>
 * {@code SocketConnection}s are usually managed by another class in this library, either an implementation representing a server or a client. These classes are responsible for
 * calling the callbacks set in this object.
 * <p>
 * None of the methods in this interface throw any checked exceptions. Exceptions in most callbacks or IO operations are caught internally by the implementation and passed to the
 * {@code onError} callback, which is one of the few callbacks that can and should not throw an exception itself. After an error, the connection is forcibly closed.
 * <p>
 * Implementations should inherit from {@link AbstractSocketConnection}, because it already contains several implemented methods and utility methods.
 * 
 * @apiNote Before version 2.1.0, this interface was an abstract class, which has been partially moved to {@link AbstractSocketConnection}. Note that the specified behavior of some
 * methods has been changed
 */
public interface SocketConnection extends java.io.Closeable {


	/**
	 * Connects this <code>SocketConnection</code> to the previously specified remote address in the constructor. If no address was specified, this method will throw an
	 * <code>UnsupportedOperationException</code>.
	 * <p>
	 * Whether this method is blocking is implementation-defined.
	 * <p>
	 * A connection timeout in milliseconds may be specified in the <b>timeout</b> parameter. If the connection has not been established within this timeout, the handler set using
	 * {@link #setOnTimeout(Runnable)} is called and the connection is closed, and if this method is blocking, it will return. Depending on the implementation and underlying
	 * protocol, a timeout may occur earlier or never and may instead cause the <code>onError</code> callback to be called.
	 * 
	 * @param timeout The connection timeout in milliseconds. Disabled if 0
	 * @see #setOnWritable(ThrowingRunnable)
	 */
	public abstract void connect(int timeout);

	/**
	 * Reads data received from the peer host on this connection.
	 * <p>
	 * Whether this method is blocking is implementation-defined. If no data was available, <code>null</code> is returned, or the method blocks until data is available.
	 * 
	 * @return The read data or <code>null</code> if no data is available.
	 */
	public abstract byte[] read();

	/**
	 * Writes data to this connection for delivery to the peer host.
	 * <p>
	 * A call to this method is equivalent to a call to
	 * 
	 * <pre>
	 * <code>
	 * {@link #write(byte[], int, int) write}(data, 0, data.length);
	 * </code>
	 * </pre>
	 * 
	 * @param data The data to write
	 * @see #write(byte[], int, int)
	 * @see #writeQueue(byte[])
	 */
	public default void write(byte[] data) {
		this.write(data, 0, data.length);
	}

	/**
	 * Writes data to this connection for delivery to the peer host.
	 * <p>
	 * Whether this method is blocking is implementation-defined. A call to this method may store data in a temporary write buffer if the underlying socket is busy. An application
	 * should try to respect the value of {@link #isWritable()} to reduce memory consumption by such write buffer if a lot of data is being written.
	 * <p>
	 * If this method is called before the <code>onConnect</code> event, the data is queued in a temporary buffer and written out when the socket connects.
	 * 
	 * @param data The data to write
	 * @param offset The start index of the data to write in the <b>data</b> byte array
	 * @param length The total number of bytes to write from the <b>data</b> byte array, starting at <b>offset</b>
	 * @throws IllegalArgumentException If <b>offset</b> is negative or if the end index would exceed the length of the array
	 * @since 1.5
	 * @see #write(byte[])
	 * @see #writeQueue(byte[], int, int)
	 * @see #setOnWritable(ThrowingRunnable)
	 */
	public abstract void write(byte[] data, int offset, int length);

	/**
	 * Similar to {@link #write(byte[])}, except that no attempt will be made to immediately flush the data to the socket, if supported by the implementation.
	 * <p>
	 * A call to this method is equivalent to a call to
	 * 
	 * <pre>
	 * <code>
	 * {@link #writeQueue(byte[], int, int) writeQueue}(data, 0, data.length);
	 * </code>
	 * </pre>
	 * 
	 * @param data The data to write
	 * @see #writeQueue(byte[], int, int)
	 * @see #write(byte[])
	 */
	public default void writeQueue(byte[] data) {
		this.writeQueue(data, 0, data.length);
	}

	/**
	 * Similar to {@link #write(byte[], int, int)}, except that no attempt will be made to immediately flush the data to the socket, if supported by the implementation.
	 * 
	 * @param data The data to write
	 * @param offset The start index of the data to write in the <b>data</b> byte array
	 * @param length The total number of bytes to write from the <b>data</b> byte array, starting at <b>offset</b>
	 * @throws IllegalArgumentException If <b>offset</b> is negative or if the end index would exceed the length of the array
	 * @since 1.5
	 * @see #writeQueue(byte[])
	 * @see #write(byte[], int, int)
	 */
	public void writeQueue(byte[] data, int offset, int length);

	/**
	 * Writes the given <b>string</b> encoded using {@code UTF-8} to this connection for delivery to the peer host.
	 * 
	 * @param string The string
	 * @since 1.6
	 * @see #write(byte[])
	 */
	public default void write(String string) {
		this.write(string.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	/**
	 * Attempts to flush any queued data after a call to {@link #writeQueue(byte[])} or data that could not be written previously because the socket was busy.
	 * <p>
	 * Whether this method is blocking is implementation-defined.
	 * 
	 * @return <code>true</code> if all data could be written to the socket
	 * @see #write(byte[])
	 * @see #writeQueue(byte[])
	 */
	public abstract boolean flush();

	/**
	 * Closes this connection after all remaining data has been flushed to the socket, which may not be immediately.
	 */
	@Override
	public abstract void close();

	/**
	 * Similar to {@link #close()}, except that the connection is closed immediately, without waiting for data to be flushed to the socket.
	 * <p>
	 * {@link #isConnected()} should return <code>false</code> immediately after calling this method.
	 */
	public abstract void destroy();

	/**
	 * Returns <code>true</code> if this socket is connected.
	 * 
	 * @return <code>true</code> if this socket is connected
	 */
	public abstract boolean isConnected();

	/**
	 * Returns the {@linkplain SocketAddress address} of the remote host.
	 * 
	 * @return The address of the peer host
	 */
	public abstract SocketAddress getRemoteAddress();

	/**
	 * Returns the local {@linkplain SocketAddress address} of this connection.
	 * 
	 * @return The local address of this connection
	 */
	public abstract SocketAddress getLocalAddress();

	/**
	 * Returns the last time any data was sent over this connection, either incoming or outgoing, as returned by {@link System#currentTimeMillis()}.
	 * 
	 * @return The last time any data was sent over this connection in milliseconds
	 */
	public abstract long getLastIOTime();

	/**
	 * Returns <code>true</code> if this socket is writable, meaning data passed to {@link #write(byte[])} will not be buffered but written to the socket directly.
	 * 
	 * @return <code>true</code> if this socket is writable
	 */
	public abstract boolean isWritable();

	/**
	 * Enables or disables read blocking. If set to <code>true</code>, the implementation will attempt to block incoming data from being processed and delay it until this is set to
	 * <code>false</code> again. Note that the implementation may still fire <code>onData</code> events while this option is set to <code>true</code>.
	 * 
	 * @param block Whether to attempt to block incoming data
	 */
	public abstract void setReadBlock(boolean block);


	/**
	 * Sets a possibly different remote address a client claims to be or act on behalf of.
	 * <p>
	 * For example, if a connection received by a server was proxied through a proxy, this should be set to the actual client address.
	 * 
	 * @param apparentRemoteAddress The apparent address of the peer
	 */
	public void setApparentRemoteAddress(SocketAddress apparentRemoteAddress);

	/**
	 * Returns the apparent remote address previously set by {@link #setApparentRemoteAddress(SocketAddress)}, or the address returned by {@link #getRemoteAddress()} if none was
	 * set.
	 * 
	 * @return The apparent remote address
	 */
	public SocketAddress getApparentRemoteAddress();


	/**
	 * Sets a callback that is called when this socket is connected and ready to receive or send data.
	 * 
	 * @param onConnect The callback
	 */
	public void setOnConnect(ThrowingRunnable onConnect);

	/**
	 * Sets a callback that is called when the connect operation started using {@link #connect(int)} times out.
	 * 
	 * @param onTimeout The callback
	 */
	public void setOnTimeout(ThrowingRunnable onTimeout);

	/**
	 * Sets a callback that is called when data is received on this connection.
	 * 
	 * @param onData The callback
	 */
	public void setOnData(ThrowingConsumer<byte[]> onData);

	/**
	 * Sets a callback that is called when this socket is ready for writing after a {@link #write(byte[])} or {@link #connect(int)} operation. This event is not called if the
	 * socket was previously already writable. This event is also not called during a <code>write(byte[])</code> call to allow the handler to safely call that method without being
	 * called again synchronously.
	 * 
	 * @param onWritable The callback
	 */
	public void setOnWritable(ThrowingRunnable onWritable);

	/**
	 * Sets a callback that is called when this connection closes and can no longer receive or send data.
	 * 
	 * @param onClose The callback
	 */
	public void setOnClose(ThrowingRunnable onClose);

	/**
	 * Sets a callback that is called when an error occurs on this connection.
	 * <p>
	 * This callback is usually followed by a <code>onClose</code> (set using {@link SocketConnection#setOnClose(Runnable)}) callback.
	 * 
	 * @param onError The callback
	 */
	public void setOnError(Consumer<Throwable> onError);
}
