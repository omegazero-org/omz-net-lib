/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.client;

import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import org.omegazero.net.common.NetworkApplicationBuilder;
import org.omegazero.net.socket.SocketConnection;

/**
 * A {@code NetworkApplicationBuilder} for creating {@link NetClientManager}s.
 * 
 * @since 2.1.0
 */
public abstract class NetClientManagerBuilder extends NetworkApplicationBuilder {


	@Override
	public abstract NetClientManager build();


	@Override
	public NetClientManagerBuilder transportType(TransportType transportType) {
		return (NetClientManagerBuilder) super.transportType(transportType);
	}

	@Override
	public NetClientManagerBuilder encrypted(boolean encrypted) {
		return (NetClientManagerBuilder) super.encrypted(encrypted);
	}

	@Override
	public NetClientManagerBuilder workerCreator(Function<SocketConnection, Consumer<Runnable>> workerCreator) {
		return (NetClientManagerBuilder) super.workerCreator(workerCreator);
	}

	@Override
	public NetClientManagerBuilder sslContext(SSLContext sslContext) {
		return (NetClientManagerBuilder) super.sslContext(sslContext);
	}

	@Override
	public NetClientManagerBuilder set(String option, Object value) {
		return (NetClientManagerBuilder) super.set(option, value);
	}
}
