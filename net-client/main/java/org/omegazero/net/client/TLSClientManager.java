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
package org.omegazero.net.client;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.omegazero.net.client.params.ConnectionParameters;
import org.omegazero.net.client.params.TLSConnectionParameters;
import org.omegazero.net.socket.ChannelConnection;
import org.omegazero.net.socket.impl.TLSConnection;
import org.omegazero.net.socket.provider.SocketChannelProvider;
import org.omegazero.net.util.TrustManagerUtil;

public class TLSClientManager extends TCPClientManager {

	private TrustManager[] trustManagers;

	public TLSClientManager() {
		this(null, (Collection<X509Certificate>) null);
	}

	public TLSClientManager(Consumer<Runnable> worker) {
		this(worker, (Collection<X509Certificate>) null);
	}

	public TLSClientManager(Consumer<Runnable> worker, Collection<X509Certificate> additionalTrustCertificates) {
		super(worker);
		try{
			this.trustManagers = TrustManagerUtil.getTrustManagersWithAdditionalCertificates(additionalTrustCertificates);
		}catch(GeneralSecurityException | IOException e){
			throw new RuntimeException("Error while initializing trust manager", e);
		}
	}

	public TLSClientManager(Consumer<Runnable> worker, TrustManager[] trustManagers) {
		super(worker);
		this.trustManagers = trustManagers;
	}


	@Override
	protected ChannelConnection createConnection(SelectionKey selectionKey, ConnectionParameters params) throws IOException {
		try{
			if(!(params instanceof TLSConnectionParameters))
				throw new IllegalArgumentException("params must be an instance of " + TLSConnectionParameters.class.getName());
			TLSConnectionParameters tlsParams = (TLSConnectionParameters) params;

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, this.trustManagers, null);
			return new TLSConnection(selectionKey, new SocketChannelProvider(), params.getRemote(), context, true, tlsParams.getAlpnNames(), tlsParams.getSniOptions());
		}catch(GeneralSecurityException | IOException e){
			throw new RuntimeException("Error while creating TLS client connection", e);
		}
	}

	@Override
	protected void handleConnect(ChannelConnection conn) {
		try{
			((TLSConnection) conn).doTLSHandshake();
		}catch(Exception e){
			conn.handleError(e);
		}
	}
}
