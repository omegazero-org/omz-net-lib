/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.nio.client;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.omegazero.net.client.NetClientManager;
import org.omegazero.net.client.NetClientManagerBuilder;

/**
 * The {@code NetClientManagerBuilder} for the <i>nio</i> implementation.
 * 
 * @since 2.1.0
 */
public class NioClientManagerBuilder extends NetClientManagerBuilder {


	@Override
	public NetClientManager build() {
		super.prepareBuild();
		if(!super.encrypted){
			if(super.transportType == TransportType.STREAM){
				return new PlainTCPClientManager(super.workerCreator);
			}else if(super.transportType == TransportType.DATAGRAM){
				return new PlainUDPClientManager(super.workerCreator);
			}else
				throw new UnsupportedOperationException("Transport type " + super.transportType);
		}else{
			if(super.transportType == TransportType.STREAM){
				if(super.sslContext == null)
					super.sslContext = newSSLContext("TLS");
				if(!super.sslContext.getProtocol().equals("TLS"))
					throw new UnsupportedOperationException("sslContext must have 'TLS' set as the protocol");
				return new TLSClientManager(super.workerCreator, super.sslContext);
			}else if(super.transportType == TransportType.DATAGRAM){
				if(super.sslContext == null)
					super.sslContext = newSSLContext("DTLS");
				if(!super.sslContext.getProtocol().equals("DTLS"))
					throw new UnsupportedOperationException("sslContext must have 'DTLS' set as the protocol");
				return new DTLSClientManager(super.workerCreator, super.sslContext);
			}else
				throw new UnsupportedOperationException("Transport type " + super.transportType);
		}
	}


	private static SSLContext newSSLContext(String protocol) {
		try{
			SSLContext sslContext = SSLContext.getInstance(protocol);
			sslContext.init(null, null, null);
			return sslContext;
		}catch(GeneralSecurityException e){
			throw new UnsupportedOperationException("Cannot create a SSLContext with protocol '" + protocol + "'", e);
		}
	}
}
