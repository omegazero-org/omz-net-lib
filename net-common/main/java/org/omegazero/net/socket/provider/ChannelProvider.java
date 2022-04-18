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
package org.omegazero.net.socket.provider;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.omegazero.net.socket.ChannelConnection;

/**
 * Used by {@link ChannelConnection}s for I/O and wraps a {@code java.nio} channel.
 */
public interface ChannelProvider {


	/**
	 * Initialized this {@link ChannelProvider}.
	 * 
	 * @param connection The {@link ChannelConnection} this {@code ChannelProvider} is for
	 * @param key The selection key of the connection
	 */
	public void init(ChannelConnection connection, SelectionKey key);


	/**
	 * Connects the underlying {@code java.nio} channel to the given remote address.
	 * 
	 * @param remote The remote address
	 * @param timeout If applicable, the connection attempt will cancel after this time in milliseconds
	 * @return {@code true} if the channel connected immediately
	 * @throws IOException If an IO error occurs
	 */
	public boolean connect(SocketAddress remote, int timeout) throws IOException;

	/**
	 * Closes the underlying {@code java.nio} channel.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	public void close() throws IOException;


	/**
	 * Reads data from the underlying {@code java.nio} channel into the given buffer.
	 * 
	 * @param buf The buffer to write data to
	 * @return The number of bytes read
	 * @throws IOException If an IO error occurs
	 */
	public int read(ByteBuffer buf) throws IOException;

	/**
	 * Writes data from the given buffer to the underlying {@code java.nio} channel.
	 * 
	 * @param buf The buffer to read from
	 * @return The number of bytes written
	 * @throws IOException If an IO error occurs
	 */
	public int write(ByteBuffer buf) throws IOException;


	/**
	 * Called by the {@link ChannelConnection} to indicate write backlog has started because the outgoing socket buffer is full.
	 */
	public void writeBacklogStarted();

	/**
	 * Called by the {@link ChannelConnection} to indicate write backlog has ended.
	 */
	public void writeBacklogEnded();


	/**
	 * If given {@code true}, the underlying selection key should stop listening for {@linkplain SelectionKey#OP_READ read ops}.
	 * 
	 * @param block Whether to block read events
	 */
	public void setReadBlock(boolean block);


	/**
	 * Returns {@code true} if the underlying {@code java.nio} channel is connected.
	 * 
	 * @return {@code true} if the channel is connected
	 */
	public boolean isAvailable();


	/**
	 * Returns the {@link SelectionKey} of this {@code ChannelProvider}.
	 * 
	 * @return The {@code SelectionKey}
	 * @since 1.7
	 */
	public SelectionKey getSelectionKey();
}
