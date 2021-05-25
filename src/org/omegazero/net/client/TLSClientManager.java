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
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.omegazero.net.client.params.InetConnectionParameters;
import org.omegazero.net.client.params.TLSConnectionParameters;
import org.omegazero.net.socket.InetConnection;
import org.omegazero.net.socket.impl.TLSConnection;
import org.omegazero.net.util.SSLUtil;

public class TLSClientManager extends TCPClientManager {

	private static X509TrustManager trustManagerDefault;

	private TrustManager[] trustManagers;

	public TLSClientManager() {
		this(null, null);
	}

	public TLSClientManager(Consumer<Runnable> worker) {
		this(worker, null);
	}

	public TLSClientManager(Consumer<Runnable> worker, Collection<String> additionalTrustCertificateFiles) {
		super(worker);
		try{
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, null);
			TLSClientManager.addDefaultCertificates(ks);
			if(additionalTrustCertificateFiles != null)
				for(String f : additionalTrustCertificateFiles){
					ks.setCertificateEntry(f, SSLUtil.loadCertificateFromPEM(f));
				}
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);
			this.trustManagers = tmf.getTrustManagers();
		}catch(GeneralSecurityException | IOException e){
			throw new RuntimeException("Error while initializing trust manager", e);
		}
	}


	@Override
	protected InetConnection createConnection(SocketChannel socketChannel, InetConnectionParameters params) throws IOException {
		try{
			if(!(params instanceof TLSConnectionParameters))
				throw new IllegalArgumentException("params must be an instance of " + TLSConnectionParameters.class.getName());
			TLSConnectionParameters tlsParams = (TLSConnectionParameters) params;

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, this.trustManagers, null);
			return new TLSConnection(socketChannel, params.getRemote(), context, true, super.worker, tlsParams.getAlpnNames(), tlsParams.getSniOptions());
		}catch(GeneralSecurityException | IOException e){
			throw new RuntimeException("Error while creating TLS client connection", e);
		}
	}

	@Override
	protected void handleConnect(InetConnection conn) throws IOException {
		((TLSConnection) conn).doTLSHandshake();
	}


	private static void addDefaultCertificates(KeyStore ks) throws GeneralSecurityException {
		for(X509Certificate cert : TLSClientManager.trustManagerDefault.getAcceptedIssuers()){
			ks.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
		}
	}

	static{
		try{
			TrustManagerFactory deftfm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			deftfm.init((KeyStore) null);
			for(TrustManager tm : deftfm.getTrustManagers()){
				if(tm instanceof X509TrustManager){
					TLSClientManager.trustManagerDefault = (X509TrustManager) tm;
					break;
				}
			}
		}catch(GeneralSecurityException e){
			throw new RuntimeException("Error while getting default trust manager", e);
		}
	}
}
