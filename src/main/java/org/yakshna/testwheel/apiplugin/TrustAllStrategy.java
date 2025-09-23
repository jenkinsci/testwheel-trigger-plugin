/**
 * 
 */
package org.yakshna.testwheel.apiplugin;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.hc.core5.ssl.TrustStrategy;

/**
 * @author YT-Prakash
 *
 */
public class TrustAllStrategy implements TrustStrategy {

	@Override
	public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		return true;
	}

}
