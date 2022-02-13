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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustManagerUtil {

	private static X509TrustManager trustManagerDefault;


	/**
	 * Generates an array of trust managers which are configured to accept all certificates that the default trust manager trusts in addition to the certificates passed in
	 * <b>additionalTrustCertificates</b>.
	 * 
	 * @param additionalTrustCertificates The collection of certificates to trust in addition to the default ones. May be <code>null</code> to not trust any additional
	 *                                    certificates
	 * @return The trust manager array
	 * @throws GeneralSecurityException
	 * @throws IOException
	 * @see #addDefaultCertificates(KeyStore)
	 */
	public static TrustManager[] getTrustManagersWithAdditionalCertificates(Collection<X509Certificate> additionalTrustCertificates)
			throws GeneralSecurityException, IOException {
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);
		TrustManagerUtil.addDefaultCertificates(ks);
		if(additionalTrustCertificates != null)
			for(X509Certificate f : additionalTrustCertificates){
				ks.setCertificateEntry(f.getIssuerX500Principal().getName(), f);
			}
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);
		return tmf.getTrustManagers();
	}

	/**
	 * Loads the certificates from the given file paths using {@link SSLUtil#loadCertificateFromPEM(String)} and generates an array of trust managers using
	 * {@link #getTrustManagersWithAdditionalCertificates(Collection)}.<br>
	 * <br>
	 * The given certificate files must only contain a single certificate each.
	 * 
	 * @param additionalTrustCertificateFiles The collection of file paths to load additional certificates from
	 * @return The trust manager array
	 * @throws GeneralSecurityException
	 * @throws IOException
	 * @see #getTrustManagersWithAdditionalCertificates(Collection)
	 */
	public static TrustManager[] getTrustManagersWithAdditionalCertificateFiles(Collection<String> additionalTrustCertificateFiles)
			throws GeneralSecurityException, IOException {
		Collection<X509Certificate> certs = new java.util.LinkedList<>();
		for(String f : additionalTrustCertificateFiles){
			certs.add(SSLUtil.loadCertificateFromPEM(f));
		}
		return TrustManagerUtil.getTrustManagersWithAdditionalCertificates(certs);
	}

	/**
	 * Adds the certificates that the default trust manager trusts to the given <code>KeyStore</code>.
	 * 
	 * @param ks
	 * @throws GeneralSecurityException
	 */
	public static void addDefaultCertificates(KeyStore ks) throws GeneralSecurityException {
		for(X509Certificate cert : TrustManagerUtil.trustManagerDefault.getAcceptedIssuers()){
			ks.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
		}
	}

	/**
	 * Returns a trust manager array containing a single trust manager that accepts all certificates.<br>
	 * <br>
	 * <b>Warning:</b> Trusting all certificates when using SSL is inherently dangerous as it defeats most of the purpose of it. This should be used for testing purposes only.
	 * 
	 * @return The trust manager array
	 */
	public static TrustManager[] getTrustAllManager() {
		return new TrustManager[] { new X509TrustManager(){

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		} };
	}


	static{
		try{
			TrustManagerFactory deftfm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			deftfm.init((KeyStore) null);
			for(TrustManager tm : deftfm.getTrustManagers()){
				if(tm instanceof X509TrustManager){
					TrustManagerUtil.trustManagerDefault = (X509TrustManager) tm;
					break;
				}
			}
		}catch(GeneralSecurityException e){
			throw new RuntimeException("Error while getting default trust manager", e);
		}
	}
}
