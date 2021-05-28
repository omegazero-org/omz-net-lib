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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.omegazero.net.socket.InetSocketConnection;

public class PlainConnection extends InetSocketConnection {

	public PlainConnection(SocketChannel socket) throws IOException {
		this(socket, null);
	}

	public PlainConnection(SocketChannel socket, InetSocketAddress remote) throws IOException {
		super(socket, remote);
		this.createBuffers();
	}


	@Override
	protected void createBuffers() {
		super.readBuf = ByteBuffer.allocate(8192);
		super.writeBuf = ByteBuffer.allocate(8192);
	}


	@Override
	public synchronized byte[] read() {
		try{
			if(!super.isConnected())
				return null;
			super.readBuf.clear();
			if(super.readFromSocket() >= 0){
				super.readBuf.flip();
				if(super.readBuf.hasRemaining()){
					byte[] a = new byte[super.readBuf.remaining()];
					super.readBuf.get(a);
					super.readBuf.compact();
					return a;
				}else
					return null;
			}
		}catch(Exception e){
			super.handleError(e);
		}
		// error or read returned below 0 (eof)
		super.close();
		return null;
	}

	@Override
	public synchronized void write(byte[] a) {
		try{
			if(!super.isConnected()){
				super.queueWrite(a);
				return;
			}
			super.writeBuf.clear();
			int written = 0;
			while(written < a.length){
				int wr = Math.min(super.writeBuf.remaining(), a.length - written);
				super.writeBuf.put(a, written, wr);
				super.writeBuf.flip();
				super.writeToSocket();
				super.writeBuf.compact();
				written += wr;
			}
		}catch(Throwable e){
			super.handleError(e);
		}
	}
}
