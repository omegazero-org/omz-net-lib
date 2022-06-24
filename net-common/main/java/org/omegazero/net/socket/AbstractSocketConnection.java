/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.socket;

import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.common.util.SimpleAttachmentContainer;
import org.omegazero.common.util.function.ThrowingConsumer;
import org.omegazero.common.util.function.ThrowingRunnable;
import org.omegazero.net.common.UnhandledException;
import org.omegazero.net.util.SyncWorker;

/**
 * A {@link SocketConnection} containing implementations for several interface methods and utility methods.
 * 
 * @since 2.1.0
 * @apiNote Before version 2.1.0, parts of this class were in {@code SocketConnection}.
 */
public abstract class AbstractSocketConnection extends SimpleAttachmentContainer implements SocketConnection {

	private ThrowingRunnable onConnect;
	private ThrowingRunnable onTimeout;
	private ThrowingConsumer<byte[]> onData;
	private ThrowingRunnable onWritable;
	private ThrowingRunnable onClose;
	private Consumer<Throwable> onError;
	private Consumer<AbstractSocketConnection> onLocalConnect;
	private Consumer<AbstractSocketConnection> onLocalClose;

	private SocketAddress apparentRemoteAddress;

	private Consumer<Runnable> worker = new SyncWorker();

	private List<byte[]> writeQueue = new LinkedList<>();

	private boolean closed = false;

	/**
	 * Lock for read operations.
	 */
	protected final Object readLock = new Object();
	/**
	 * Lock for write operations.
	 */
	protected final Object writeLock = new Object();


	@Override
	public boolean hasConnected() {
		return this.writeQueue == null; // writeQueue gets deleted in handleConnect/flushWriteQueue
	}


	@Override
	public final void setApparentRemoteAddress(SocketAddress apparentRemoteAddress) {
		this.apparentRemoteAddress = apparentRemoteAddress;
	}

	@Override
	public final SocketAddress getApparentRemoteAddress() {
		if(this.apparentRemoteAddress != null)
			return this.apparentRemoteAddress;
		else
			return this.getRemoteAddress();
	}


	@Override
	public final void setOnConnect(ThrowingRunnable onConnect) {
		this.onConnect = onConnect;
	}

	@Override
	public final void setOnTimeout(ThrowingRunnable onTimeout) {
		this.onTimeout = onTimeout;
	}

	@Override
	public final void setOnData(ThrowingConsumer<byte[]> onData) {
		this.onData = onData;
	}

	@Override
	public final void setOnWritable(ThrowingRunnable onWritable) {
		this.onWritable = onWritable;
	}

	@Override
	public final void setOnClose(ThrowingRunnable onClose) {
		this.onClose = onClose;
	}

	@Override
	public final void setOnError(Consumer<Throwable> onError) {
		this.onError = onError;
	}


	public final void setOnLocalClose(Consumer<AbstractSocketConnection> onLocalClose) {
		if(this.onLocalClose != null)
			throw new IllegalStateException("onLocalClose is already set");
		this.onLocalClose = onLocalClose;
	}

	public final void setOnLocalConnect(Consumer<AbstractSocketConnection> onLocalConnect) {
		if(this.onLocalConnect != null)
			throw new IllegalStateException("onLocalConnect is already set");
		this.onLocalConnect = onLocalConnect;
	}


	/**
	 * Returns the worker for this {@code AbstractSocketConnection}.
	 * 
	 * @return The worker
	 * @see #setWorker(Consumer)
	 */
	public Consumer<Runnable> getWorker() {
		return this.worker;
	}

	/**
	 * Sets the worker for this {@code AbstractSocketConnection}. This worker is used to run all callbacks except {@code onError} and may be used to run the callbacks in a
	 * different thread.
	 * <p>
	 * The worker must execute all tasks in the order they were given and should not execute tasks concurrently.
	 * <p>
	 * The default worker is {@link SyncWorker}.
	 * 
	 * @param worker The worker
	 */
	public void setWorker(Consumer<Runnable> worker) {
		this.worker = Objects.requireNonNull(worker);
	}


	/**
	 * Returns the read lock. This object is locked every time a read operation that changes the internal state of the connection is performed.
	 * 
	 * @return The read lock
	 * @since 1.4
	 */
	public final Object getReadLock() {
		return this.readLock;
	}

	/**
	 * Returns the write lock. This object is locked every time a write operation that changes the internal state of the connection is performed.
	 * 
	 * @return The write lock
	 * @since 1.4
	 */
	public final Object getWriteLock() {
		return this.writeLock;
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


	public final void handleConnect() {
		this.runAsync(this::runOnConnect);
	}

	public final void handleTimeout() {
		this.runAsync(this::runOnTimeout);
	}

	/**
	 * Called by classes managing this {@link SocketConnection} if data was received using {@link #read()}. This method calls the {@code onData} callback.
	 * 
	 * @param data The data that was received on this connection
	 * @return <code>false</code> if no <code>onData</code> handler was set upon entry of this method
	 * @see #setOnData(ThrowingConsumer)
	 */
	public final boolean handleData(byte[] data) {
		boolean s = this.onData == null;
		this.runAsync(this::runOnData, data);
		return s;
	}

	/**
	 * Called by subclasses if this socket is writable. This method calls the {@code onWritable} callback.
	 * 
	 * @see #setOnWritable(ThrowingRunnable)
	 */
	public final void handleWritable() {
		this.runAsync(this::runOnWritable);
	}

	/**
	 * Called by subclasses or classes managing this {@link SocketConnection} if this connection closed. This method calls the {@code onClose} callback on the first invocation of
	 * this method.
	 * 
	 * @see #setOnClose(ThrowingRunnable)
	 */
	public final void handleClose() {
		synchronized(this){
			if(this.closed)
				return;
			this.closed = true;
		}
		this.runAsync(this::runOnClose);
	}


	/**
	 * Called by subclasses or classes managing this {@link SocketConnection} if an error occurred in a callback. This method calls the {@code onError} callback and
	 * {@linkplain #destroy() forcibly closes} this connection.
	 * <p>
	 * Unlike the other {@code handle} methods, this method always runs synchronously.
	 * 
	 * @param e The error
	 * @throws UnhandledException If no {@code onError} handler is set
	 * @see #setOnError(Consumer)
	 */
	public final void handleError(Throwable e) {
		try{
			if(this.onError != null){
				this.onError.accept(e);
			}else
				throw new UnhandledException(e);
		}finally{
			this.destroy();
		}
	}


	private void runOnConnect() throws Exception {
		this.flushWriteQueue();
		if(this.onConnect != null)
			this.onConnect.run();
		if(this.isWritable())
			this.runOnWritable();
	}

	private void runOnTimeout() throws Exception {
		try{
			if(this.onTimeout != null)
				this.onTimeout.run();
			else if(this.onError != null)
				this.onError.accept(new java.io.IOException("connect timed out"));
		}finally{
			this.destroy();
		}
	}

	private void runOnData(byte[] data) throws Exception {
		if(this.onData != null)
			this.onData.accept(data);
	}

	private void runOnWritable() throws Exception {
		// onWritable can happen before onConnect, for example when flushing the write backlog, but that should be suppressed
		// this is called anyway in handleConnect if socket is writable
		if(this.hasConnected() && this.onWritable != null)
			this.onWritable.run();
	}

	private void runOnClose() throws Exception {
		if(this.onClose != null)
			this.onClose.run();
	}


	protected final void runAsync(ThrowingRunnable runnable) {
		this.worker.accept(() -> {
			try{
				runnable.run();
			}catch(Exception e){
				this.handleError(e);
			}
		});
	}

	protected final <T> void runAsync(ThrowingConsumer<T> runnable, T value) {
		this.worker.accept(() -> {
			try{
				runnable.accept(value);
			}catch(Exception e){
				this.handleError(e);
			}
		});
	}
}
