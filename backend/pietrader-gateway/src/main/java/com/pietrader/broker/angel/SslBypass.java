package com.pietrader.broker.angel;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class SslBypass {

    public static void disableSSL() {
	try {
	    TrustManager[] trustAllCerts = new TrustManager[]{
		    new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() { return null; }
			public void checkClientTrusted(X509Certificate[] certs, String authType) { }
			public void checkServerTrusted(X509Certificate[] certs, String authType) { }
		    }
	    };

	    SSLContext sc = SSLContext.getInstance("TLS");
	    sc.init(null, trustAllCerts, new java.security.SecureRandom());

	    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	    HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

	    System.out.println("SSL verification disabled (TEST MODE)");

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
