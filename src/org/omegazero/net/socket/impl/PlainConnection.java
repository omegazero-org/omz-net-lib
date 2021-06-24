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
package org.omegazero.net.socket.impl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.provider.ChannelProvider;

public class PlainConnection extends ChannelConnection {

	public PlainConnection(SelectionKey selectionKey, ChannelProvider provider) throws IOException {
		this(selectionKey, provider, null);
	}

	public PlainConnection(SelectionKey selectionKey, ChannelProvider provider, SocketAddress remote) throws IOException {
		super(selectionKey, provider, remote);

		this.createBuffers();
	}


	@Override
	protected void createBuffers() {
		super.readBuf = ByteBuffer.allocate(8192);
		super.writeBuf = ByteBuffer.allocate(8192);
	}


	@Override
	public byte[] read() {
		try{
			if(!super.isConnected())
				return null;
			synchronized(super.readBuf){
				super.readBuf.clear();
				if(super.readFromSocket() >= 0){
					super.readBuf.flip();
					if(super.readBuf.hasRemaining()){
						byte[] a = new byte[super.readBuf.remaining()];
						super.readBuf.get(a);
						return a;
					}else
						return null;
				}else
					super.close();
			}
		}catch(Exception e){
			super.handleError(e);
		}
		return null;
	}

	@Override
	public void write(byte[] data) {
		try{
			synchronized(this){
				if(!super.hasConnected()){
					super.queueWrite(data);
					return;
				}
			}
			synchronized(super.writeBuf){
				super.writeBuf.clear();
				int written = 0;
				while(written < data.length){
					int wr = Math.min(super.writeBuf.remaining(), data.length - written);
					super.writeBuf.put(data, written, wr);
					super.writeBuf.flip();
					super.writeToSocket();
					super.writeBuf.compact();
					written += wr;
				}
			}
		}catch(Throwable e){
			super.handleError(e);
		}
	}
}
