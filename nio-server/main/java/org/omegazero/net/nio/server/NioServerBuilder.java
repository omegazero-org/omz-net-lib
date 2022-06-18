/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.nio.server;

import org.omegazero.net.server.NetServer;
import org.omegazero.net.server.NetServerBuilder;

/**
 * The {@code NetServerBuilder} for the <i>nio</i> implementation.
 * 
 * @since 2.1.0
 */
public class NioServerBuilder extends NetServerBuilder {


	@Override
	public NetServer build() {
		super.prepareBuild();
		if(super.listenPath != null)
			throw new UnsupportedOperationException("This implementation does not support local filesystem servers (listenPath)");
		if(!super.encrypted){
			if(super.applicationLayerProtocols != null)
				throw new UnsupportedOperationException("applicationLayerProtocols is only supported for TLS servers");
			if(super.transportType == TransportType.STREAM){
				return new PlainTCPServer(super.bindAddresses, super.ports, super.connectionBacklog, super.workerCreator, super.connectionIdleTimeout * 1000L);
			}else if(super.transportType == TransportType.DATAGRAM){
				return new PlainUDPServer(super.bindAddresses, super.ports, super.workerCreator, super.connectionIdleTimeout * 1000L, 8192);
			}else
				throw new UnsupportedOperationException("Transport type " + super.transportType);
		}else{
			if(super.sslContext == null)
				throw new UnsupportedOperationException("sslContext must be given with encryption enabled");
			if(super.transportType == TransportType.STREAM){
				if(!super.sslContext.getProtocol().equals("TLS"))
					throw new UnsupportedOperationException("sslContext must have 'TLS' set as the protocol");
				TLSServer server = new TLSServer(super.bindAddresses, super.ports, super.connectionBacklog, super.workerCreator, super.connectionIdleTimeout * 1000L,
						super.sslContext);
				server.setSupportedApplicationLayerProtocols(super.applicationLayerProtocols);
				return server;
			}else if(super.transportType == TransportType.DATAGRAM){
				if(!super.sslContext.getProtocol().equals("DTLS"))
					throw new UnsupportedOperationException("sslContext must have 'DTLS' set as the protocol");
				DTLSServer server = new DTLSServer(super.bindAddresses, super.ports, super.workerCreator, super.connectionIdleTimeout * 1000L, 8192, super.sslContext);
				server.setSupportedApplicationLayerProtocols(super.applicationLayerProtocols);
				return server;
			}else
				throw new UnsupportedOperationException("Transport type " + super.transportType);
		}
	}
}
