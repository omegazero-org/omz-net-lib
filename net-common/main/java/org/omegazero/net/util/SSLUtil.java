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

import com.mendix.ssltools.PrivateKeyReader;

public final class SSLUtil {

	private SSLUtil() {
	}


	public static ServerSocketFactory getServerSocketFactory() {
		return ServerSocketFactory.getDefault();
	}

	public static SSLContext getSSLContextFromKeyStore(String filename, String password, String keypassword) throws GeneralSecurityException, IOException {
		return SSLUtil.getSSLContextFromKeyStore("TLS", filename, password, keypassword);
	}

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


	public static SSLContext getSSLContextFromPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return SSLUtil.getSSLContextFromPEM("TLS", keyFile, certFile);
	}

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

	public static SSLServerSocketFactory getSecureServerSocketFactory(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).getServerSocketFactory();
	}

	public static SSLEngine getSSLEngineWithPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).createSSLEngine();
	}


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


	public static PrivateKey loadPrivateKeyFromPEM(String keyFile) throws GeneralSecurityException, IOException {
		String keyFileData = new String(Files.readAllBytes(Paths.get(keyFile)));
		PrivateKey key = PrivateKeyReader.read(keyFileData);
		return key;
	}

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
