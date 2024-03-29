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
package org.omegazero.net.nio.socket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;

import org.omegazero.common.util.function.ThrowingRunnable;
import org.omegazero.net.nio.socket.provider.ChannelProvider;
import org.omegazero.net.socket.AbstractSocketConnection;

/**
 * Represents a {@link AbstractSocketConnection} based on a java.nio {@link SocketChannel} or {@link DatagramChannel}.
 * 
 * @apiNote Before version 2.1.0, this class was in package {@code org.omegazero.net.socket}.
 */
public abstract class ChannelConnection extends AbstractSocketConnection {


	private final ChannelProvider provider;

	private final SelectableChannel socket;
	private final SocketAddress remoteAddress;
	private final SocketAddress localAddress;
	private long lastIOTime;

	protected ByteBuffer readBuf;
	protected ByteBuffer writeBuf;

	private Deque<byte[]> writeBacklog = new LinkedList<>();
	private ByteBuffer writeBufTemp;
	private boolean pendingClose = false;
	private boolean localClose = false;

	public ChannelConnection(SelectionKey selectionKey, ChannelProvider provider) throws IOException {
		this(selectionKey, provider, null);
	}

	public ChannelConnection(SelectionKey selectionKey, ChannelProvider provider, SocketAddress remote) throws IOException {
		this.provider = provider;

		this.socket = selectionKey != null ? selectionKey.channel() : null;

		provider.init(this, selectionKey);

		SocketAddress socketRemote = null;
		if(this.socket instanceof SocketChannel)
			socketRemote = ((SocketChannel) this.socket).getRemoteAddress();
		else if(this.socket instanceof DatagramChannel && remote == null)
			// if representing a client, remote must be given anyways and for incoming requests to a server, the socket will represent the server socket
			// (which doesnt have a remote address, so it must be provided)
			throw new IllegalArgumentException("remote address must always be given for DatagramChannels");
		if(socketRemote != null && remote != null)
			throw new AlreadyConnectedException();
		else if(socketRemote != null)
			this.remoteAddress = socketRemote;
		else
			this.remoteAddress = remote;
		if(this.socket instanceof NetworkChannel)
			this.localAddress = ((NetworkChannel) this.socket).getLocalAddress();
		else
			this.localAddress = null;

		this.lastIOTime = System.currentTimeMillis();

		this.ensureNonBlocking();
	}


	/**
	 * Must be called by subclass constructors. This method creates the {@link #readBuf} and {@link #writeBuf} buffers and any additional buffers required.
	 */
	protected abstract void createBuffers();

	/**
	 * Forcibly closes this connection.
	 */
	protected void close0() {
		synchronized(this){
			if(this.localClose)
				return;
			this.localClose = true;
		}
		super.localClose();
		try{
			this.provider.close();
		}catch(IOException e){
			throw new RuntimeException("Error while closing channel", e);
		}
	}


	@Override
	public void connect(int timeout) {
		try{
			this.ensureNonBlocking();
			if(this.remoteAddress == null)
				throw new UnsupportedOperationException("Cannot connect because no remote address was specified");
			if(this.provider.connect(this.remoteAddress, timeout))
				super.localConnect();
		}catch(Exception e){
			super.handleError(e);
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @implSpec This method does not write remaining data in this {@link ChannelConnection}'s {@code writeBuf}. Subclasses must override this method to implement this behavior.
	 */
	@Override
	public boolean flush() {
		return this.flushWriteBacklog();
	}

	@Override
	public final void close() {
		if(!this.localClose && !this.flush()) // data still pending
			this.pendingClose = true;
		else
			this.close0();
	}

	@Override
	public final void destroy() {
		this.close0();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @return <code>true</code> if this socket is open and available for reading or writing
	 */
	@Override
	public boolean isConnected() {
		return this.provider.isAvailable() && (this.socket == null || this.socket.isOpen());
	}

	@Override
	public SocketAddress getRemoteAddress() {
		return this.remoteAddress;
	}

	@Override
	public SocketAddress getLocalAddress() {
		return this.localAddress;
	}

	@Override
	public long getLastIOTime() {
		return this.lastIOTime;
	}

	@Override
	public boolean isWritable() {
		return this.isConnected() && this.isWriteBacklogEmpty();
	}

	@Override
	public void setReadBlock(boolean block) {
		this.provider.setReadBlock(block);
	}


	public ChannelProvider getProvider() {
		return this.provider;
	}


	/**
	 * Attempts to write any remaining data in the write buffer and write backlog to the socket and returns <code>true</code> if all data could be written.<br>
	 * <br>
	 * This method is equivalent to {@link #flush()}.
	 * 
	 * @return <code>true</code> if all pending data was written to the socket
	 */
	public boolean flushWriteBacklog() {
		try{
			return this.flushWriteBacklog0();
		}catch(Exception e){
			this.handleError(e);
		}
		return false;
	}


	private void ensureNonBlocking() throws IOException {
		if(this.socket != null && this.socket.isBlocking())
			this.socket.configureBlocking(false);
	}

	private int addToWriteBacklog() {
		synchronized(super.writeLock){
			if(!this.writeBuf.hasRemaining())
				return 0;
			if(this.writeBufTemp == null){
				this.writeBufTemp = ByteBuffer.allocate(this.writeBuf.capacity());
				this.writeBufTemp.flip(); // set to no remaining bytes
			}

			boolean already = this.writeBacklog.size() > 0 || this.writeBufTemp.hasRemaining();
			byte[] wb = new byte[this.writeBuf.remaining()];
			this.writeBuf.get(wb);
			this.writeBacklog.add(wb);

			if(!already)
				this.provider.writeBacklogStarted();
			return wb.length;
		}
	}

	private boolean flushWriteBacklog0() throws IOException {
		synchronized(super.writeLock){
			if(this.isWriteBacklogEmpty())
				return true;
			if(this.writeBufTemp.hasRemaining()){
				this.provider.write(this.writeBufTemp);
				if(this.writeBufTemp.hasRemaining()) // the socket still was not able to write the whole content
					return false;
			}
			while(this.writeBacklog.size() > 0){
				byte[] data = this.writeBacklog.remove();
				this.writeBufTemp.clear();
				this.writeBufTemp.put(data);
				this.writeBufTemp.flip();
				this.provider.write(this.writeBufTemp);
				if(this.writeBufTemp.hasRemaining()) // could not write entire data packet to socket
					break;
			}
			if(this.writeBufTemp.hasRemaining())
				return false;
			this.provider.writeBacklogEnded();
		}
		// no need to lock writeBuf here anymore
		super.handleWritable();
		if(this.pendingClose){
			this.pendingClose = false;
			this.close0();
		}
		return true;
	}

	private boolean isWriteBacklogEmpty() {
		synchronized(super.writeLock){
			return this.writeBacklog.size() == 0 && (this.writeBufTemp == null || !this.writeBufTemp.hasRemaining());
		}
	}


	/**
	 * Reads data into the {@linkplain #readBuf read buffer} using this {@link ChannelConnection}'s {@link ChannelProvider}.
	 * 
	 * @return The number of bytes read
	 * @throws IOException If an IO error occurs
	 */
	protected final int readFromSocket() throws IOException {
		this.lastIOTime = System.currentTimeMillis();
		return this.provider.read(this.readBuf);
	}

	/**
	 * Writes data from the {@linkplain #writeBuf write buffer} using this {@link ChannelConnection}'s {@link ChannelProvider}.
	 * 
	 * @return The number of bytes written, including buffered bytes
	 * @throws IOException If an IO error occurs
	 */
	protected final int writeToSocket() throws IOException {
		synchronized(super.writeLock){
			long start = System.currentTimeMillis();
			this.lastIOTime = start;
			if(!this.isWriteBacklogEmpty()){
				// there is still data in the write backlog, so dont even attempt to write to socket because it is full
				return this.addToWriteBacklog();
			}
			int written = 0;
			while(this.writeBuf.hasRemaining()){
				int w = this.provider.write(this.writeBuf);
				if(w == 0){
					w = this.addToWriteBacklog();
					if(this.writeBuf.hasRemaining())
						throw new IllegalStateException("writeBuf has data after addToWriteBacklog()");
				}
				written += w;
			}
			return written;
		}
	}


	protected final void writeBuffered(byte[] data, int offset, int length, boolean flush, ByteBuffer targetBuffer, ThrowingRunnable writeOut) {
		if(data != null && (offset < 0 || offset + length > data.length))
			throw new IllegalArgumentException("offset=" + offset + " length=" + length + " data.length=" + data.length);
		try{
			synchronized(this.writeLock){
				if(!super.hasConnected()){
					if(data != null && length > 0){
						if(offset == 0 && length == data.length)
							super.queueWrite(data);
						else
							super.queueWrite(java.util.Arrays.copyOfRange(data, offset, offset + length));
					}
					return;
				}
				if(!flush && targetBuffer.remaining() >= length){
					targetBuffer.put(data, offset, length);
				}else if(data != null && length > 0){
					int written = 0;
					while(written < length){
						int wr = Math.min(targetBuffer.remaining(), length - written);
						targetBuffer.put(data, written + offset, wr);
						targetBuffer.flip();
						writeOut.run();
						targetBuffer.clear();
						written += wr;
					}
				}else if(targetBuffer.position() > 0){
					targetBuffer.flip();
					writeOut.run();
					targetBuffer.clear();
				}
			}
		}catch(Exception e){
			super.handleError(e);
		}
	}
}
