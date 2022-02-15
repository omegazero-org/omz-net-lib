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
package org.omegazero.net.socket;

import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.omegazero.net.common.ThrowingConsumer;
import org.omegazero.net.common.ThrowingRunnable;

/**
 * Represents any type of connection between the local and a remote host.
 */
public abstract class SocketConnection implements AutoCloseable {

	private ThrowingRunnable onConnect;
	private ThrowingRunnable onTimeout;
	private ThrowingConsumer<byte[]> onData;
	private ThrowingRunnable onWritable;
	private ThrowingRunnable onClose;
	private Consumer<Throwable> onError;
	private Consumer<SocketConnection> onLocalConnect;
	private Consumer<SocketConnection> onLocalClose;

	private SocketAddress apparentRemoteAddress;

	private Object attachment;

	private List<byte[]> writeQueue = new LinkedList<>();

	private boolean closed = false;

	protected final Object readLock = new Object();
	protected final Object writeLock = new Object();


	/**
	 * Connects this <code>SocketConnection</code> to the previously specified remote address in the constructor. If no address was specified, this method will throw an
	 * <code>UnsupportedOperationException</code><br>
	 * <br>
	 * This function is non-blocking.<br>
	 * <br>
	 * A connection timeout in milliseconds may be specified in the <b>timeout</b> parameter. If the connection has not been established within this timeout, the handler set
	 * using {@link #setOnTimeout(Runnable)} is called and the connection is closed. Depending on the implementation and underlying protocol, a timeout may occur earlier or
	 * never and may instead cause the <code>onError</code> callback to be called.
	 * 
	 * @param timeout The connection timeout in milliseconds. Disabled if 0
	 */
	public abstract void connect(int timeout);

	/**
	 * Reads data received from the peer host on this connection. <br>
	 * <br>
	 * This function is non-blocking. If no data was available, <code>null</code> is returned.
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
	public void write(byte[] data) {
		this.write(data, 0, data.length);
	}

	/**
	 * Writes data to this connection for delivery to the peer host.
	 * <p>
	 * This function is non-blocking and may store data in a temporary write buffer if the underlying socket is busy. An application should try to respect the value of
	 * {@link #isWritable()} to reduce memory consumption by such write buffer if a lot of data is being written.
	 * <p>
	 * If this method is called before the <code>onConnect</code> event, the data is queued in a temporary buffer and written out when the socket connects.
	 * 
	 * @param data   The data to write
	 * @param offset The start index of the data to write in the <b>data</b> byte array
	 * @param length The total number of bytes to write from the <b>data</b> byte array, starting at <b>offset</b>
	 * @throws IllegalArgumentException If <b>offset</b> is negative or if the end index would exceed the length of the array
	 * @since 1.5
	 * @see #write(byte[])
	 * @see #writeQueue(byte[], int, int)
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
	public void writeQueue(byte[] data) {
		this.writeQueue(data, 0, data.length);
	}

	/**
	 * Similar to {@link #write(byte[], int, int)}, except that no attempt will be made to immediately flush the data to the socket, if supported by the implementation.
	 * 
	 * @param data   The data to write
	 * @param offset The start index of the data to write in the <b>data</b> byte array
	 * @param length The total number of bytes to write from the <b>data</b> byte array, starting at <b>offset</b>
	 * @throws IllegalArgumentException If <b>offset</b> is negative or if the end index would exceed the length of the array
	 * @since 1.5
	 * @see #writeQueue(byte[])
	 * @see #write(byte[], int, int)
	 * @implNote The default behavior is to call {@link #write(byte[], int, int)}. Subclasses should override this method.
	 */
	public void writeQueue(byte[] data, int offset, int length) {
		this.write(data, offset, length);
	}

	/**
	 * Attempts to flush any queued data after a call to {@link #writeQueue(byte[])} or data that could not be written previously because the socket was busy.
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
	 * Similar to {@link #close()}, except that the connection is closed immediately, without waiting for data to be flushed to the socket.<br>
	 * <br>
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
	 * Enables or disables read blocking. If set to <code>true</code>, the implementation will attempt to block incoming data from being processed and delay it until this is
	 * set to <code>false</code> again. Note that the implementation may still fire <code>onData</code> events while this option is set to <code>true</code>.
	 * 
	 * @param block Whether to attempt to block incoming data
	 */
	public abstract void setReadBlock(boolean block);


	/**
	 * Sets a possibly different remote address a client claims to be or act on behalf of.<br>
	 * <br>
	 * For example, if a connection received by a server was proxied through a proxy, this should be set to the actual client address.
	 * 
	 * @param apparentRemoteAddress The apparent address of the peer
	 */
	public final void setApparentRemoteAddress(SocketAddress apparentRemoteAddress) {
		this.apparentRemoteAddress = apparentRemoteAddress;
	}

	/**
	 * 
	 * @return The apparent remote address previously set by {@link SocketConnection#setApparentRemoteAddress(SocketAddress)}, or the address returned by
	 *         {@link SocketConnection#getRemoteAddress()} if none was set
	 */
	public final SocketAddress getApparentRemoteAddress() {
		if(this.apparentRemoteAddress != null)
			return this.apparentRemoteAddress;
		else
			return this.getRemoteAddress();
	}


	public final void handleConnect() {
		try{
			this.flushWriteQueue();
			if(this.onConnect != null)
				this.onConnect.run();
			if(this.isWritable())
				this.handleWritable();
		}catch(Exception e){
			this.handleError(e);
		}
	}

	public final void handleTimeout() {
		try{
			if(this.onTimeout != null)
				this.onTimeout.run();
			this.destroy();
		}catch(Exception e){
			this.handleError(e);
		}
	}

	/**
	 * 
	 * @param data The data that was received on this connection
	 * @return <code>false</code> if no <code>onData</code> handler was set
	 */
	public final boolean handleData(byte[] data) {
		if(this.onData == null)
			return false;
		try{
			this.onData.accept(data);
		}catch(Exception e){
			this.handleError(e);
		}
		return true;
	}

	public final void handleWritable() {
		try{
			// onWritable can happen before onConnect, for example when flushing the write backlog, but that should be suppressed
			// this is called anyway in handleConnect if socket is writable
			if(this.hasConnected() && this.onWritable != null)
				this.onWritable.run();
		}catch(Exception e){
			this.handleError(e);
		}
	}

	public final void handleError(Throwable e) {
		if(this.onError != null){
			this.onError.accept(e);
			this.destroy();
		}else
			throw new RuntimeException("Socket Error", e);
	}

	public final void handleClose() {
		if(this.closed)
			return;
		this.closed = true;
		try{
			if(this.onClose != null)
				this.onClose.run();
		}catch(Exception e){
			this.handleError(e);
		}
	}


	/**
	 * Sets a callback that is called when this socket is connected and ready to receive or send data.
	 * 
	 * @param onConnect The callback
	 */
	public final void setOnConnect(ThrowingRunnable onConnect) {
		this.onConnect = onConnect;
	}

	/**
	 * Sets a callback that is called when the connect operation started using {@link #connect(int)} times out.
	 * 
	 * @param onTimeout The callback
	 */
	public final void setOnTimeout(ThrowingRunnable onTimeout) {
		this.onTimeout = onTimeout;
	}

	/**
	 * Sets a callback that is called when data is received on this connection.
	 * 
	 * @param onData The callback
	 */
	public final void setOnData(ThrowingConsumer<byte[]> onData) {
		this.onData = onData;
	}

	/**
	 * Sets a callback that is called when this socket is ready for writing after a {@link #write(byte[])} or {@link #connect(int)} operation. This event is not called if the
	 * socket was previously already writable. This event is also not called during a <code>write(byte[])</code> call to allow the handler to safely call that method without
	 * being called again synchronously.
	 * 
	 * @param onWritable The callback
	 */
	public final void setOnWritable(ThrowingRunnable onWritable) {
		this.onWritable = onWritable;
	}

	/**
	 * Sets a callback that is called when this connection closes and can no longer receive or send data.
	 * 
	 * @param onClose The callback
	 */
	public final void setOnClose(ThrowingRunnable onClose) {
		this.onClose = onClose;
	}

	/**
	 * Sets a callback that is called when an error occurs on this connection.<br>
	 * <br>
	 * This callback is usually followed by a <code>onClose</code> (set using {@link SocketConnection#setOnClose(Runnable)}) callback.
	 * 
	 * @param onError The callback
	 */
	public final void setOnError(Consumer<Throwable> onError) {
		this.onError = onError;
	}

	public final void setOnLocalClose(Consumer<SocketConnection> onLocalClose) {
		if(this.onLocalClose != null)
			throw new IllegalStateException("onLocalClose is already set");
		this.onLocalClose = onLocalClose;
	}

	public final void setOnLocalConnect(Consumer<SocketConnection> onLocalConnect) {
		if(this.onLocalConnect != null)
			throw new IllegalStateException("onLocalConnect is already set");
		this.onLocalConnect = onLocalConnect;
	}

	/**
	 * Returns <code>true</code> if the <i>onConnect</i> event has ever executed. This is already <code>true</code> while running the event.
	 * 
	 * @return <code>true</code> if the <i>onConnect</i> event has ever fired
	 */
	public final boolean hasConnected() {
		return this.writeQueue == null; // writeQueue gets deleted in handleConnect/flushWriteQueue
	}


	/**
	 * Called by implementing classes if this connection was established immediately upon calling {@link #connect(int)}. May be used internally by the connection manager.
	 */
	protected final void localConnect() {
		if(this.onLocalConnect != null)
			this.onLocalConnect.accept(this);
		else
			this.handleConnect();
	}

	/**
	 * Called by implementing classes when this connection was closed using any close method ({@link #close()} or {@link #destroy()}).
	 */
	protected final void localClose() {
		if(this.onLocalClose != null)
			this.onLocalClose.accept(this);
		else
			this.handleClose();
	}

	/**
	 * Called by implementing classes to queue data for writing before this connection {@linkplain #hasConnected() has connected}.
	 * 
	 * @param data The data
	 */
	protected final synchronized void queueWrite(byte[] data) {
		if(this.writeQueue != null)
			this.writeQueue.add(data);
		else
			throw new IllegalStateException("Tried to queue write after connection finished");
	}

	private synchronized void flushWriteQueue() {
		List<byte[]> wq = this.writeQueue;
		this.writeQueue = null;
		for(byte[] d : wq){
			this.write(d);
		}
	}


	public final Object getAttachment() {
		return this.attachment;
	}

	public final void setAttachment(Object attachment) {
		this.attachment = attachment;
	}


	/**
	 * Returns the read lock. This object is locked every time a read operation that changes the internal state of the connection is performed.
	 * 
	 * @return The read lock
	 * @since 1.4
	 */
	public Object getReadLock() {
		return this.readLock;
	}

	/**
	 * Returns the write lock. This object is locked every time a write operation that changes the internal state of the connection is performed.
	 * 
	 * @return The write lock
	 * @since 1.4
	 */
	public Object getWriteLock() {
		return this.writeLock;
	}
}
