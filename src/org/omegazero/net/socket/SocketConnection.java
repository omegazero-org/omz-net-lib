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
public abstract class SocketConnection {

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
	 * Writes data to this connection for delivery to the peer host. <br>
	 * <br>
	 * This function is non-blocking and may store data in a temporary write buffer if the underlying socket is busy. An application should try to respect the value of
	 * {@link #isWritable()} to reduce memory consumption by such write buffer if a lot of data is being written.<br>
	 * <br>
	 * If this method is called before the <code>onConnect</code> event, the data is queued in a temporary buffer and written out when the socket connects.
	 * 
	 * @param data The data to be written to this connection
	 */
	public abstract void write(byte[] data);

	/**
	 * Closes this connection.
	 */
	public abstract void close();

	/**
	 * 
	 * @return <code>true</code> if this socket is connected.
	 */
	public abstract boolean isConnected();

	/**
	 * 
	 * @return The address of the peer host
	 */
	public abstract SocketAddress getRemoteAddress();

	/**
	 * 
	 * @return The local address of this connection
	 */
	public abstract SocketAddress getLocalAddress();

	/**
	 * 
	 * @return The last time any data was sent over this connection, either incoming or outgoing, as returned by {@link System#currentTimeMillis()}
	 */
	public abstract long getLastIOTime();

	/**
	 * 
	 * @return <code>true</code> if this socket is writable, meaning data passed to {@link #write(byte[])} will not be buffered but written to the socket directly
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
			if(this.onConnect != null)
				this.onConnect.run();
			this.flushWriteQueue();
		}catch(Exception e){
			this.handleError(e);
		}
	}

	public final void handleTimeout() {
		try{
			if(this.onTimeout != null)
				this.onTimeout.run();
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
			if(this.onWritable != null)
				this.onWritable.run();
		}catch(Exception e){
			this.handleError(e);
		}
	}

	public final void handleError(Throwable e) {
		if(this.onError != null){
			this.onError.accept(e);
			this.close();
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
	 * Sets a callback that is called when this socket is ready for writing again after a {@link #write(byte[])} operation.
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
	 * 
	 * @return <code>true</code> if the onConnect event has ever fired
	 */
	public final boolean hasConnected() {
		return this.writeQueue == null; // writeQueue gets deleted in handleConnect/flushWriteQueue
	}


	protected final void localConnect() {
		if(this.onLocalConnect != null)
			this.onLocalConnect.accept(this);
		else
			this.handleConnect();
	}

	protected final void localClose() {
		if(this.onLocalClose != null)
			this.onLocalClose.accept(this);
		else
			this.handleClose();
	}

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
		return attachment;
	}

	public final void setAttachment(Object attachment) {
		this.attachment = attachment;
	}
}
