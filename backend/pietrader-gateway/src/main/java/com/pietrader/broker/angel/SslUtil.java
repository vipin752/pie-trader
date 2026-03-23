package com.pietrader.broker.angel;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class SslUtil {

    public static void disableSslVerification() throws Exception {
	TrustManager[] trustAllCerts = new TrustManager[]{
		new X509TrustManager() {
		    public X509Certificate[] getAcceptedIssuers() { return null; }
		    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
		    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
		}
	};

	SSLContext sc = SSLContext.getInstance("SSL");
	sc.init(null, trustAllCerts, new java.security.SecureRandom());
	HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
	HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }
}
