/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.net.common;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import org.omegazero.net.client.NetClientManagerBuilder;
import org.omegazero.net.server.NetServerBuilder;
import org.omegazero.net.socket.SocketConnection;

/**
 * A builder class for creating {@link NetworkApplication}s.
 * <p>
 * Builders for server and client manager instances are created using {@link #newServer(String)} and {@link #newClientManager(String)}.
 * 
 * @since 2.1.0
 * @see NetServerBuilder
 * @see NetClientManagerBuilder
 */
public abstract class NetworkApplicationBuilder {

	private static final Map<String, String> implAliases = new java.util.HashMap<>();

	protected TransportType transportType = TransportType.STREAM;
	protected boolean encrypted = false;
	protected Function<SocketConnection, Consumer<Runnable>> workerCreator = null;
	protected SSLContext sslContext = null;


	/**
	 * Creates the {@code NetworkApplication} from the previously set parameters.
	 * 
	 * @return The {@code NetworkApplication}
	 * @throws UnsupportedOperationException If the specific configuration is not supported by the implementation
	 * @throws IllegalArgumentException If a configuration parameter combination is invalid
	 * @throws IllegalStateException If a required parameter was not set
	 * @implSpec Implementations of this method must call {@link #prepareBuild()} first.
	 */
	public abstract NetworkApplication build();

	/**
	 * This method does parameter validation and preparations.
	 */
	protected void prepareBuild() {
		if(!this.encrypted && this.sslContext != null)
			throw new IllegalArgumentException("sslContext cannot be set if encryption is not enabled");
	}


	/**
	 * Sets the {@link TransportType} to use in this instance.
	 * <p>
	 * The default is {@link TransportType#STREAM}.
	 * 
	 * @param transportType The {@code TransportType}
	 * @return This builder
	 * @throws NullPointerException If <b>transportType</b> is {@code null}
	 */
	public NetworkApplicationBuilder transportType(TransportType transportType) {
		this.transportType = Objects.requireNonNull(transportType);
		return this;
	}

	/**
	 * Sets whether to use encryption. Specific implementation or types may require additional parameters to enable encryption.
	 * <p>
	 * The default is {@code false}.
	 * 
	 * @param encrypted Whether encryption is enabled
	 * @return This builder
	 */
	public NetworkApplicationBuilder encrypted(boolean encrypted) {
		this.encrypted = encrypted;
		return this;
	}

	/**
	 * Sets a worker creator which creates a worker instance for each connection created.
	 * <p>
	 * The default is {@code null} (no workers).
	 * 
	 * @param workerCreator The function
	 * @return This builder
	 */
	public NetworkApplicationBuilder workerCreator(Function<SocketConnection, Consumer<Runnable>> workerCreator) {
		this.workerCreator = workerCreator;
		return this;
	}

	/**
	 * Sets the {@link SSLContext} to use for encryption.
	 * <p>
	 * This method implicitly enables {@linkplain #encrypted(boolean) encryption} if the given parameter is not {@code null}, and disables it otherwise.
	 * <p>
	 * The default is {@code null}. Setting this parameter may be required by the implementation if encryption is enabled.
	 * 
	 * @param sslContext The {@code SSLContext}
	 * @return This builder
	 */
	public NetworkApplicationBuilder sslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
		this.encrypted(sslContext != null);
		return this;
	}

	/**
	 * Sets an implementation specific configuration parameter.
	 *
	 * @param option The name of the parameter
	 * @param value The value
	 * @throws IllegalArgumentException If the given parameter name is not valid
	 * @since 2.2.1
	 * @implNote By default, this method always throws an {@code IllegalArgumentException}
	 */
	public NetworkApplicationBuilder set(String option, Object value) {
		throw new IllegalArgumentException("Invalid option '" + option + "'");
	}


	private static <T extends NetworkApplicationBuilder> Class<? extends T> resolveBuilder(String implName, String typeName, Class<T> type) {
		String className;
		if(implAliases.containsKey(implName))
			className = implAliases.get(implName);
		else if(implAliases.containsKey(implName + "_" + typeName))
			className = implAliases.get(implName + "_" + typeName);
		else
			className = implName;
		try{
			Class<?> cl = Class.forName(className);
			return cl.asSubclass(type);
		}catch(ClassNotFoundException e){
			throw new IllegalArgumentException("Could not resolve implementation name '" + implName + "'", e);
		}catch(ClassCastException e){
			throw new IllegalArgumentException("Found class '" + className + "' for implementation name '" + implName + "' but class is not a '" + type.getName() + "'", e);
		}
	}


	/**
	 * Adds an implementation name alias.
	 * <p>
	 * Usually, the methods {@link #newServer(String)} and {@link #newClientManager(String)} require a class name for the <b>implementation</b> parameter. Adding a name alias
	 * allows a shorter name to be passed instead.
	 * <p>
	 * For example, adding an alias as follows:
	 * 
	 * <pre>
	 * <code>
	 * addImplementationAlias("example_server", "com.example.server.ServerBuilder");
	 * </code>
	 * </pre>
	 * 
	 * makes the following calls equivalent:
	 * 
	 * <pre>
	 * <code>
	 * newServer("com.example.server.ServerBuilder");
	 * newServer("example_server");
	 * newServer("example");
	 * </code>
	 * </pre>
	 * 
	 * The last call is allowed because when looking up an alias for servers, the given name suffixed with {@code "_server"} (or {@code "_client"} for client managers) is also
	 * tried.
	 * <p>
	 * By default, two aliases are registered:
	 * <ul>
	 * <li>{@code "nio_server"} - {@code "org.omegazero.net.nio.server.NioServerBuilder"}</li>
	 * <li>{@code "nio_client"} - {@code "org.omegazero.net.nio.client.NioClientManagerBuilder"}</li>
	 * </ul>
	 * 
	 * @param name The short name
	 * @param className The implementation builder class name
	 */
	public static void addImplementationAlias(String name, String className) {
		if(implAliases.containsKey(name))
			throw new IllegalArgumentException("Alias '" + name + "' already exists");
		implAliases.put(name, className);
	}


	/**
	 * Creates a new {@code NetServerBuilder} to create a {@code NetServer} instance.
	 * 
	 * @param implementation The implementation name or class name of the builder
	 * @return The new builder
	 * @see #addImplementationAlias(String, String)
	 */
	public static NetServerBuilder newServer(String implementation) {
		Class<? extends NetServerBuilder> cl = resolveBuilder(implementation, "server", NetServerBuilder.class);
		try{
			return cl.newInstance();
		}catch(ReflectiveOperationException e){
			throw new RuntimeException("Instantiation of '" + cl.getName() + "' failed", e);
		}
	}

	/**
	 * Creates a new {@code NetClientManagerBuilder} to create a {@code NetClientManager} instance.
	 * 
	 * @param implementation The implementation name or class name of the builder
	 * @return The new builder
	 * @see #addImplementationAlias(String, String)
	 */
	public static NetClientManagerBuilder newClientManager(String implementation) {
		Class<? extends NetClientManagerBuilder> cl = resolveBuilder(implementation, "client", NetClientManagerBuilder.class);
		try{
			return cl.newInstance();
		}catch(ReflectiveOperationException e){
			throw new RuntimeException("Instantiation of '" + cl.getName() + "' failed", e);
		}
	}


	/**
	 * An enum containing the transport type options of a network application.
	 */
	public static enum TransportType {
		/**
		 * Streaming network sockets, such as <i>TCP</i>.
		 */
		STREAM,
		/**
		 * Datagram network sockets, such as <i>UDP</i>.
		 */
		DATAGRAM;
	}


	static{
		addImplementationAlias("nio_server", "org.omegazero.net.nio.server.NioServerBuilder");
		addImplementationAlias("nio_client", "org.omegazero.net.nio.client.NioClientManagerBuilder");
	}
}
