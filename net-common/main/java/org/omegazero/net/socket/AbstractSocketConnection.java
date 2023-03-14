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

import org.omegazero.common.event.EventEmitter;
import org.omegazero.common.event.runnable.GenericRunnable;
import org.omegazero.common.util.SimpleAttachmentContainer;
import org.omegazero.net.common.UnhandledException;
import org.omegazero.net.util.SyncWorker;

/**
 * A {@link SocketConnection} containing implementations for several interface methods and utility methods.
 * 
 * @since 2.1.0
 * @apiNote Before version 2.1.0, parts of this class were in {@code SocketConnection}.
 */
public abstract class AbstractSocketConnection extends SimpleAttachmentContainer implements SocketConnection {

	/**
	 * Event ID of the {@code connect} event.
	 */
	protected static final int EV_CONNECT = 0;
	/**
	 * Event ID of the {@code timeout} event.
	 */
	protected static final int EV_TIMEOUT = 1;
	/**
	 * Event ID of the {@code data} event.
	 */
	protected static final int EV_DATA = 2;
	/**
	 * Event ID of the {@code writable} event.
	 */
	protected static final int EV_WRITABLE = 3;
	/**
	 * Event ID of the {@code close} event.
	 */
	protected static final int EV_CLOSE = 4;
	/**
	 * Event ID of the {@code error} event.
	 */
	protected static final int EV_ERROR = 5;

	/**
	 * The event emitter used for events.
	 */
	protected final EventEmitter eventEmitter;

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

	/**
	 * Creates a new {@code AbstractSocketConnection}.
	 */
	public AbstractSocketConnection(){
		this.eventEmitter = new EventEmitter();
		this.eventEmitter.reserveEventIdSpace(5);
		this.eventEmitter.createEventId("connect", EV_CONNECT);
		this.eventEmitter.createEventId("timeout", EV_TIMEOUT);
		this.eventEmitter.createEventId("data", EV_DATA);
		this.eventEmitter.createEventId("writable", EV_WRITABLE);
		this.eventEmitter.createEventId("close", EV_CLOSE);
		this.eventEmitter.createEventId("error", EV_ERROR);
	}


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
	public final SocketConnection on(String event, GenericRunnable runnable){
		this.eventEmitter.on(event, runnable);
		return this;
	}

	@Override
	public final SocketConnection once(String event, GenericRunnable runnable){
		this.eventEmitter.once(event, runnable);
		return this;
	}

	@Override
	public final SocketConnection off(String event, GenericRunnable runnable){
		this.eventEmitter.off(event, runnable);
		return this;
	}


	/**
	 * Called by classes managing this {@code SocketConnection}.
	 *
	 * @param onLocalClose The onLocalClose callback
	 */
	public final void setOnLocalClose(Consumer<AbstractSocketConnection> onLocalClose) {
		if(this.onLocalClose != null)
			throw new IllegalStateException("onLocalClose is already set");
		this.onLocalClose = onLocalClose;
	}

	/**
	 * Called by classes managing this {@code SocketConnection}.
	 *
	 * @param onLocalConnect The onLocalConnect callback
	 */
	public final void setOnLocalConnect(Consumer<AbstractSocketConnection> onLocalConnect) {
		if(this.onLocalConnect != null)
			throw new IllegalStateException("onLocalConnect is already set");
		this.onLocalConnect = onLocalConnect;
	}

	/**
	 * Called by classes managing this {@code SocketConnection}.
	 *
	 * @param runnable The callback
	 * @since 2.2.0
	 */
	public final void setDefaultErrorListener(GenericRunnable.A1<Throwable> runnable){
		this.eventEmitter.setDefaultEventListener("error", runnable);
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
	 * Sets the worker for this {@code AbstractSocketConnection}. This worker is used to run all events except {@code error} and may be used to run the callbacks in a
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
	protected final void queueWrite(byte[] data) {
		synchronized(this.writeLock){
			if(this.writeQueue != null)
				this.writeQueue.add(data);
			else
				throw new IllegalStateException("Tried to queue write after connection finished");
		}
	}

	private void flushWriteQueue() {
		synchronized(this.writeLock){
			List<byte[]> wq = this.writeQueue;
			this.writeQueue = null;
			for(byte[] d : wq){
				this.write(d);
			}
		}
	}


	/**
	 * Called internally, by subclasses or by classes managing this {@code SocketConnection} when this socket connects. This method calls the {@code connect} event.
	 */
	public final void handleConnect(){
		this.runAsync(this::runOnConnect);
	}

	/**
	 * Called internally, by subclasses or by classes managing this {@code SocketConnection} when a connection attempt times out. This method calls the {@code timeout} event.
	 */
	public final void handleTimeout(){
		this.runAsync(this::runOnTimeout);
	}

	/**
	 * Called by classes managing this {@link SocketConnection} when data was received using {@link #read()}. This method calls the {@code data} event.
	 * 
	 * @param data The data that was received on this connection
	 * @return <code>false</code> if no {@code data} event handler was set upon entry of this method
	 */
	public final boolean handleData(byte[] data) {
		boolean s = this.eventEmitter.getEventListenerCount("data") > 0;
		this.runAsync(this::runOnData, data);
		return s;
	}

	/**
	 * Called by subclasses when this socket is writable. This method calls the {@code writable} event.
	 */
	public final void handleWritable() {
		this.runAsync(this::runOnWritable);
	}

	/**
	 * Called by subclasses or classes managing this {@link SocketConnection} when this connection closed. This method calls the {@code close} event on the first invocation of
	 * this method.
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
	 * Called by subclasses or classes managing this {@link SocketConnection} when an error occurred in a callback. This method calls the {@code error} event and
	 * {@linkplain #destroy() forcibly closes} this connection.
	 * <p>
	 * Unlike the other {@code handle} methods, this method always runs synchronously.
	 * 
	 * @param e The error
	 * @throws UnhandledException If no {@code error} event handler is set
	 */
	public final void handleError(Throwable e) {
		try{
			if(this.eventEmitter.runEvent(EV_ERROR, e) == 0)
				throw new UnhandledException(e);
		}finally{
			this.destroy();
		}
	}


	private void runOnConnect(){
		this.flushWriteQueue();
		this.eventEmitter.runEvent(EV_CONNECT);
		if(this.isWritable())
			this.runOnWritable();
	}

	private void runOnTimeout(){
		try{
			if(this.eventEmitter.runEvent(EV_TIMEOUT) == 0)
				this.eventEmitter.runEvent(EV_ERROR, new java.io.IOException("connect timed out"));
		}finally{
			this.destroy();
		}
	}

	private void runOnData(byte[] data){
		this.eventEmitter.runEvent(EV_DATA, data);
	}

	private void runOnWritable(){
		// onWritable can happen before onConnect, for example when flushing the write backlog, but that should be suppressed
		// this is called anyway in handleConnect if socket is writable
		if(this.hasConnected())
			this.eventEmitter.runEvent(EV_WRITABLE);
	}

	private void runOnClose(){
		this.eventEmitter.runEvent(EV_CLOSE);
	}


	/**
	 * Delegates the given {@code Runnable} to this {@code AbstractSocketConnection}'s worker.
	 *
	 * @param runnable The runnable
	 */
	protected final void runAsync(Runnable runnable) {
		this.worker.accept(() -> {
			try{
				runnable.run();
			}catch(Exception e){
				this.handleError(e);
			}
		});
	}

	/**
	 * Delegates the given {@code Consumer} to this {@code AbstractSocketConnection}'s worker.
	 *
	 * @param runnable The consumer
	 * @param value The value to pass to the consumer
	 */
	protected final <T> void runAsync(Consumer<T> runnable, T value) {
		this.worker.accept(() -> {
			try{
				runnable.accept(value);
			}catch(Exception e){
				this.handleError(e);
			}
		});
	}
}
