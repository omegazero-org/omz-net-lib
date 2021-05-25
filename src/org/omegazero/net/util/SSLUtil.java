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
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
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
		if(filename == null)
			throw new IllegalArgumentException("Tried to create a secure server socket factory but filename is null");
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(filename)), password.toCharArray());

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, keypassword.toCharArray());

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
		return sslContext;
	}


	public static SSLContext getSSLContextFromPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		if(keyFile == null || certFile == null)
			throw new IllegalArgumentException("Tried to create a secure server socket factory but a filename is null");

		PrivateKey key = SSLUtil.loadPrivateKeyFromPEM(keyFile);
		X509Certificate cert = SSLUtil.loadCertificateFromPEM(certFile);

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setCertificateEntry("certificate", cert);
		keyStore.setKeyEntry("private-key", key, "password".toCharArray(), new Certificate[] { cert });

		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, "password".toCharArray());

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
		return sslContext;
	}

	public static SSLServerSocketFactory getSecureServerSocketFactory(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).getServerSocketFactory();
	}

	public static SSLEngine getSSLEngineWithPEM(String keyFile, String certFile) throws GeneralSecurityException, IOException {
		return getSSLContextFromPEM(keyFile, certFile).createSSLEngine();
	}


	public static byte[] readCertificatePEM(String data) {
		StringBuilder sb = new StringBuilder();
		Scanner scanner = new Scanner(data);
		byte[] rdata = null;
		while(scanner.hasNextLine()){
			String line = scanner.nextLine();
			if(line.contains("-----BEGIN CERTIFICATE-----")){
				continue;
			}else if(line.contains("-----END CERTIFICATE-----")){
				rdata = Base64.getDecoder().decode(sb.toString());
				break;
			}
			sb.append(line.trim());
		}
		scanner.close();
		if(rdata == null)
			throw new RuntimeException("Invalid PEM certificate format");
		return rdata;
	}


	public static PrivateKey loadPrivateKeyFromPEM(String keyFile) throws GeneralSecurityException, IOException {
		String keyFileData = new String(Files.readAllBytes(Paths.get(keyFile)));
		PrivateKey key = PrivateKeyReader.read(keyFileData);
		return key;
	}

	public static X509Certificate loadCertificateFromPEM(String certFile) throws GeneralSecurityException, IOException {
		String certFileData = new String(Files.readAllBytes(Paths.get(certFile)));
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		InputStream tlsStream = new ByteArrayInputStream(SSLUtil.readCertificatePEM(certFileData));
		X509Certificate cert = (X509Certificate) cf.generateCertificate(tlsStream);
		return cert;
	}
}
