/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.server;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import org.omegazero.net.common.NetworkApplicationBuilder;
import org.omegazero.net.socket.SocketConnection;

/**
 * A {@code NetworkApplicationBuilder} for creating {@link NetServer}s.
 * 
 * @since 2.1.0
 */
public abstract class NetServerBuilder extends NetworkApplicationBuilder {


	protected Collection<InetAddress> bindAddresses = null;
	protected Collection<Integer> ports = null;
	protected String listenPath = null;
	protected int connectionBacklog = 0;
	protected int connectionIdleTimeout = 0;
	protected String[] applicationLayerProtocols = null;


	@Override
	public abstract NetServer build();


	@Override
	public NetServerBuilder transportType(TransportType transportType) {
		return (NetServerBuilder) super.transportType(transportType);
	}

	@Override
	public NetServerBuilder encrypted(boolean encrypted) {
		return (NetServerBuilder) super.encrypted(encrypted);
	}

	@Override
	public NetServerBuilder workerCreator(Function<SocketConnection, Consumer<Runnable>> workerCreator) {
		return (NetServerBuilder) super.workerCreator(workerCreator);
	}

	@Override
	public NetServerBuilder sslContext(SSLContext sslContext) {
		return (NetServerBuilder) super.sslContext(sslContext);
	}

	@Override
	public NetServerBuilder set(String option, Object value) {
		return (NetServerBuilder) super.set(option, value);
	}


	/**
	 * Sets the local addresses the server should bind to.
	 * <p>
	 * This method overrides any previous local address settings.
	 * <p>
	 * {@code null} or a collection with a single {@code null} element may be passed to bind to the default addresses chosen by the system or implementation. This is also the
	 * default.
	 * 
	 * @param bindAddresses The local addresses
	 * @return This builder
	 * @throws NullPointerException If the collection has more than one element and any element is {@code null}
	 */
	public NetServerBuilder bindAddresses(Collection<InetAddress> bindAddresses) {
		if(bindAddresses != null){
			this.bindAddresses = new java.util.ArrayList<>(bindAddresses.size());
			for(InetAddress a : bindAddresses){
				if(a == null && bindAddresses.size() > 1)
					throw new NullPointerException("An element is null and collection has more than one element");
				this.bindAddresses.add(a);
			}
		}else{
			this.bindAddresses = new java.util.ArrayList<>();
			this.bindAddresses.add(null);
		}
		return this;
	}

	/**
	 * Sets the local addresses the server should bind to. See {@link #bindAddresses(Collection)}.
	 * <p>
	 * This method overrides any previous local address settings.
	 * 
	 * @param bindAddresses The local addresses
	 * @return This builder
	 * @throws NullPointerException If the collection has more than one element and any element is {@code null}
	 */
	public NetServerBuilder bindAddresses(InetAddress... bindAddresses) {
		if(bindAddresses == null)
			return this.bindAddresses((Collection<InetAddress>) null);
		this.bindAddresses = new java.util.ArrayList<>(bindAddresses.length);
		for(InetAddress a : bindAddresses){
			if(a == null && bindAddresses.length > 1)
				throw new NullPointerException("An element is null and collection has more than one element");
			this.bindAddresses.add(a);
		}
		return this;
	}

	/**
	 * Sets the single local address the server should bind to.
	 * <p>
	 * This method overrides any previous local address settings.
	 * 
	 * @param bindAddress The local address
	 * @return This builder
	 * @throws NullPointerException If the given value is {@code null}
	 */
	public NetServerBuilder bindAddress(InetAddress bindAddress) {
		if(this.bindAddresses == null)
			this.bindAddresses = new java.util.ArrayList<>();
		else
			this.bindAddresses.clear();
		this.bindAddresses.add(bindAddress);
		return this;
	}

	/**
	 * Adds a local address to bind to.
	 * 
	 * @param bindAddress The local address
	 * @return This builder
	 * @throws NullPointerException If the given value is {@code null}
	 * @see #bindAddresses(Collection)
	 */
	public NetServerBuilder addBindAddress(InetAddress bindAddress) {
		if(this.bindAddresses == null)
			this.bindAddresses = new java.util.ArrayList<>();
		this.bindAddresses.add(Objects.requireNonNull(bindAddress));
		return this;
	}

	/**
	 * Sets the network ports this server should listen on.
	 * <p>
	 * This method overrides any previous port settings.
	 * <p>
	 * This method and {@link #listenPath(String)} are mutually exclusive, but one of them is required.
	 * 
	 * @param ports The ports
	 * @return This builder
	 * @throws NullPointerException If an element is {@code null}
	 * @throws IllegalArgumentException If a port is not positive
	 */
	public NetServerBuilder ports(Collection<Integer> ports) {
		if(ports == null){
			this.ports = null;
			return this;
		}
		this.ports = new java.util.ArrayList<>(ports);
		for(Integer i : this.ports){
			if(i == null){
				this.ports.clear();
				throw new NullPointerException("An element is null");
			}else if(i <= 0)
				throw new IllegalArgumentException("A port is not positive");
		}
		return this;
	}

	/**
	 * Sets the network ports this server should listen on.
	 * <p>
	 * This method overrides any previous port settings.
	 * <p>
	 * This method and {@link #listenPath(String)} are mutually exclusive, but one of them is required.
	 * 
	 * @param ports The ports
	 * @return This builder
	 * @throws IllegalArgumentException If a port is not positive
	 */
	public NetServerBuilder ports(int... ports) {
		if(ports == null){
			this.ports = null;
			return this;
		}
		this.ports = new java.util.ArrayList<>(ports.length);
		for(int a : ports){
			if(a <= 0)
				throw new IllegalArgumentException("A port is not positive");
			this.ports.add(a);
		}
		return this;
	}

	/**
	 * Sets a single network port this server should listen on.
	 * <p>
	 * This method overrides any previous port settings.
	 * <p>
	 * This method and {@link #listenPath(String)} are mutually exclusive, but one of them is required.
	 * 
	 * @param port The port
	 * @return This builder
	 * @throws IllegalArgumentException If the port is not positive
	 */
	public NetServerBuilder port(int port) {
		if(port <= 0)
			throw new IllegalArgumentException("port is not positive");
		if(this.ports == null)
			this.ports = new java.util.ArrayList<>();
		else
			this.ports.clear();
		this.ports.add(port);
		return this;
	}

	/**
	 * Adds a network port this server should listen on.
	 * <p>
	 * This method and {@link #listenPath(String)} are mutually exclusive, but one of them is required.
	 * 
	 * @param port The port
	 * @return This builder
	 * @throws IllegalArgumentException If the port is not positive
	 */
	public NetServerBuilder addPort(int port) {
		if(port <= 0)
			throw new IllegalArgumentException("port is not positive");
		if(this.ports == null)
			this.ports = new java.util.ArrayList<>();
		this.ports.add(port);
		return this;
	}

	/**
	 * Sets the local file system path the server should listen on.
	 * <p>
	 * This method and {@link #ports(Collection)} (and other methods to set ports) are mutually exclusive, but one of them is required.
	 * 
	 * @param listenPath The path
	 * @return This builder
	 */
	public NetServerBuilder listenPath(String listenPath) {
		this.listenPath = listenPath;
		return this;
	}

	/**
	 * Sets the maximum length of the queue for pending connections, if applicable.
	 * <p>
	 * The default, {@code 0}, may be set to let the system or implementation choose an appropriate value.
	 * 
	 * @param connectionBacklog The queue length
	 * @return This builder
	 * @throws IllegalArgumentException If the given value is negative
	 */
	public NetServerBuilder connectionBacklog(int connectionBacklog) {
		if(connectionBacklog < 0)
			throw new IllegalArgumentException("connectionBacklog is negative");
		this.connectionBacklog = connectionBacklog;
		return this;
	}

	/**
	 * Sets the minimum time in seconds to keep an idle connection with no traffic.
	 * <p>
	 * The default, {@code 0}, may be set to disable idle timeouts.
	 * 
	 * @param connectionIdleTimeout The idle timeout in seconds
	 * @return This builder
	 * @throws IllegalArgumentException If the given value is negative
	 */
	public NetServerBuilder connectionIdleTimeout(int connectionIdleTimeout) {
		if(connectionIdleTimeout < 0)
			throw new IllegalArgumentException("connectionIdleTimeout is negative");
		this.connectionIdleTimeout = connectionIdleTimeout;
		return this;
	}

	/**
	 * Sets the application protocol name options the server presents, for example during <i>TLS Application-Layer Protocol Negotiation</i>. The elements in this list should be
	 * ordered from most-preferred to least-preferred protocol name.
	 * <p>
	 * The default is {@code null} (protocol presented by client is selected, or negotiation is disabled).
	 * 
	 * @param applicationLayerProtocols The protocol names
	 * @return This builder
	 */
	public NetServerBuilder applicationLayerProtocols(Collection<String> applicationLayerProtocols) {
		if(applicationLayerProtocols != null)
			this.applicationLayerProtocols = applicationLayerProtocols.toArray(new String[applicationLayerProtocols.size()]);
		else
			this.applicationLayerProtocols = null;
		return this;
	}

	/**
	 * Sets the application protocol name options the server presents. See {@link #applicationLayerProtocols(Collection)}.
	 * 
	 * @param applicationLayerProtocols The protocol names
	 * @return This builder
	 */
	public NetServerBuilder applicationLayerProtocols(String... applicationLayerProtocols) {
		this.applicationLayerProtocols = applicationLayerProtocols;
		return this;
	}


	@Override
	protected void prepareBuild() {
		super.prepareBuild();
		if(!(this.ports != null ^ this.listenPath != null))
			throw new IllegalArgumentException("Exactly one of ports or listenPath must be set");
		if(this.ports.isEmpty())
			throw new IllegalStateException("ports is empty");
		if(this.bindAddresses == null || this.bindAddresses.size() < 1)
			this.bindAddresses((Collection<InetAddress>) null);
	}
}
