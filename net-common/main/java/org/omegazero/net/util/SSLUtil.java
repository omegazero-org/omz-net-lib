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
package org.omegazero.net.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * Contains utility methods for managing SSL/TLS objects and keys/certificates.
 */
public final class SSLUtil {

	private SSLUtil() {
	}


	/**
	 * Returns the default server socket factory.
	 * 
	 * @return The default server socket factory
	 * @deprecated Use {@link ServerSocketFactory#getDefault()}
	 */
	public static ServerSocketFactory getServerSocketFactory() {
		return ServerSocketFactory.getDefault();
	}

	/**
	 * Creates a {@link SSLContext} with the "{@code TLS}" protocol from a Java key store stored in a file identified by <b>filename</b>.
	 * 
	 * @param filename    The key store file
	 * @param password    The file password
	 * @param keypassword The key password
	 * @return The {@code SSLContext}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @see #getSSLContextFromKeyStore(String, String, String, String)
	 * @see SSLContext#getInstance(String)
	 */
	public static SSLContext getSSLContextFromKeyStore(String filename, String password, String keypassword) throws GeneralSecurityException, IOException {
		return SSLUtil.getSSLContextFromKeyStore("TLS", filename, password, keypassword);
	}

	/**
	 * Creates a {@link SSLContext} from a Java key store stored in a file identified by <b>filename</b>.
	 * 
	 * @param protocol    The name of the protocol the {@code SSLContext} will be used for
	 * @param filename    The key store file
	 * @param password    The file password
	 * @param keypassword The key password
	 * @return The {@code SSLContext}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @see #getSSLContextFromKeyStore(String, String, String)
	 * @see SSLContext#getInstance(String)
	 */
	public static SSLContext getSSLContextFromKeyStore(String protocol, String filename, String password, String keypassword) throws GeneralSecurityException, IOException {
		if(filename == null)
			throw new IllegalArgumentException("Tried to create a SSLContext but filename is null");
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(filename)), password.toCharArray());

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, keypassword.toCharArray());

		SSLContext sslContext = SSLContext.getInstance(protocol);
		sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
		return sslContext;
	}


	/**
	 * Creates a {@link SSLContext} with the "{@code TLS}" protocol from PEM-encoded key and certificate files.
	 * 
	 * @param keyFile  The key file
	 * @param certFile The certificate file
	 * @return The {@code SSLContext}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @see #getSSLContextFromPEM(String, String, String)
	 * @see SSLContext#getInstance(String)
	 */
	public static SSLContext getSSLContextFromPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return SSLUtil.getSSLContextFromPEM("TLS", keyFile, certFile);
	}

	/**
	 * Creates a {@link SSLContext} from PEM-encoded key and certificate files.
	 * 
	 * @param protocol The name of the protocol the {@code SSLContext} will be used for
	 * @param keyFile  The key file
	 * @param certFile The certificate file
	 * @return The {@code SSLContext}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @see #getSSLContextFromPEM(String, String)
	 * @see SSLContext#getInstance(String)
	 */
	public static SSLContext getSSLContextFromPEM(String protocol, String keyFile, String certFile) throws GeneralSecurityException, IOException {
		if(keyFile == null || certFile == null)
			throw new IllegalArgumentException("Tried to create a SSLContext but a filename is null");

		PrivateKey key = SSLUtil.loadPrivateKeyFromPEM(keyFile);
		X509Certificate[] cert = SSLUtil.loadCertificatesFromPEM(certFile);

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setKeyEntry("private-key", key, "password".toCharArray(), cert);

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, "password".toCharArray());

		SSLContext sslContext = SSLContext.getInstance(protocol);
		sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
		return sslContext;
	}

	/**
	 * Creates a {@link SSLServerSocketFactory} from PEM-encoded key and certificate files.
	 * 
	 * @param keyFile  The key file
	 * @param certFile The certificate file
	 * @return The {@code SSLServerSocketFactory}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @deprecated Use {@link #getSSLContextFromPEM(String, String)} and {@link SSLContext#getServerSocketFactory()}
	 */
	@Deprecated
	public static SSLServerSocketFactory getSecureServerSocketFactory(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).getServerSocketFactory();
	}

	/**
	 * Creates a {@link SSLEngine} from PEM-encoded key and certificate files.
	 * 
	 * @param keyFile  The key file
	 * @param certFile The certificate file
	 * @return The {@code SSLEngine}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @deprecated Use {@link #getSSLContextFromPEM(String, String)} and {@link SSLContext#createSSLEngine()}
	 */
	@Deprecated
	public static SSLEngine getSSLEngineWithPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).createSSLEngine();
	}


	/**
	 * Reads one or more encoded certificates from the given string <b>data</b> and returns the decoded bytes.
	 * <p>
	 * The given <b>data</b> is expected to contain one or more blocks of the following format:
	 * 
	 * <pre>
	 * <code>
	 * -----BEGIN CERTIFICATE-----
	 * &lt;base64-encoded certificate data&gt;
	 * -----END CERTIFICATE-----
	 * </code>
	 * </pre>
	 * 
	 * @param data The string data
	 * @return The list of decoded certificate blocks
	 * @throws IOException If the format is invalid
	 */
	public static List<byte[]> readCertificatePEM(String data) throws IOException {
		Scanner scanner = new Scanner(data);
		List<byte[]> certs = new LinkedList<>();
		StringBuilder sb = new StringBuilder();
		while(scanner.hasNextLine()){
			String line = scanner.nextLine().trim();
			if(line.length() < 1)
				continue;

			if(line.contains("-----BEGIN CERTIFICATE-----")){
				sb = new StringBuilder();
			}else if(line.contains("-----END CERTIFICATE-----")){
				certs.add(Base64.getDecoder().decode(sb.toString()));
				sb = null;
			}else if(sb != null){
				sb.append(line);
			}else{
				scanner.close();
				throw new IOException("Unexpected data between certificates");
			}
		}
		scanner.close();
		if(sb != null)
			throw new IOException("Incomplete certificate at end of file");
		return certs;
	}


	/**
	 * Reads a {@link PrivateKey} from the given <b>keyFile</b>.
	 * 
	 * @param keyFile The key file
	 * @return The {@code PrivateKey}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 * @see PrivateKeyReader#read(String)
	 */
	public static PrivateKey loadPrivateKeyFromPEM(String keyFile) throws GeneralSecurityException, IOException {
		String keyFileData = new String(Files.readAllBytes(Paths.get(keyFile)));
		PrivateKey key = PrivateKeyReader.read(keyFileData);
		return key;
	}

	/**
	 * Reads a single {@link X509Certificate} from the given <b>certFile</b>.
	 * 
	 * @param certFile The certificate file
	 * @return The {@code X509Certificate}
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 */
	public static X509Certificate loadCertificateFromPEM(String certFile) throws GeneralSecurityException, IOException {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		String certFileData = new String(Files.readAllBytes(Paths.get(certFile)));
		List<byte[]> certData = SSLUtil.readCertificatePEM(certFileData);
		if(certData.size() > 1)
			throw new IOException("Expected single certificate in file '" + certFile + "' but received " + certData.size());
		byte[] cd = certData.get(0);
		InputStream cdstream = new ByteArrayInputStream(cd);
		return (X509Certificate) cf.generateCertificate(cdstream);
	}

	/**
	 * Reads one or more {@link X509Certificate} from the given <b>certFile</b>.
	 * 
	 * @param certFile The certificate file
	 * @return The {@code X509Certificate}s
	 * @throws GeneralSecurityException If an SSL error occurs
	 * @throws IOException              If an IO error occurs while reading the file
	 */
	public static X509Certificate[] loadCertificatesFromPEM(String certFile) throws GeneralSecurityException, IOException {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		String certFileData = new String(Files.readAllBytes(Paths.get(certFile)));
		List<byte[]> certData = SSLUtil.readCertificatePEM(certFileData);
		X509Certificate[] certs = new X509Certificate[certData.size()];
		int i = 0;
		for(byte[] cd : certData){
			InputStream cdstream = new ByteArrayInputStream(cd);
			certs[i++] = (X509Certificate) cf.generateCertificate(cdstream);
		}
		return certs;
	}
}
