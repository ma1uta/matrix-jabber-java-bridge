/*
 * Copyright sablintolya@gmai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.ma1uta.mjjb.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.netty.handler.ssl.SslContext;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Bridge certificate.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes( {@JsonSubTypes.Type(PKCS12Cert.class), @JsonSubTypes.Type(PemCert.class)})
public abstract class Cert {

    /**
     * Managers that trust all certificates.
     */
    public static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[] {
        new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }
        }
    };

    @JsonProperty("disable_ssl_validation")
    private boolean disableValidation = false;

    public boolean isDisableValidation() {
        return disableValidation;
    }

    public void setDisableValidation(boolean disableValidation) {
        this.disableValidation = disableValidation;
    }

    /**
     * Create Netty SSL context.
     *
     * @return Netty SSL context.
     * @throws IOException              when cannot load certificate.
     * @throws GeneralSecurityException when cannot create netty context.
     */
    public abstract SslContext createNettyContext() throws IOException, GeneralSecurityException;

    /**
     * Create Java SSL context.
     *
     * @return Java SSL context.
     * @throws IOException              when cannot load certificate.
     * @throws GeneralSecurityException when cannot create jdk context.
     */
    public abstract SSLContext createJavaContext() throws IOException, GeneralSecurityException;

    /**
     * Provide certificate type.
     *
     * @return PEM or PKCS12.
     */
    public abstract CertificateType getType();

    protected SSLContext load(KeyStore keyStore, char[] password) throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), isDisableValidation() ? TRUST_ALL_CERTS : tmf.getTrustManagers(), new SecureRandom());
        return sslContext;

    }
}
