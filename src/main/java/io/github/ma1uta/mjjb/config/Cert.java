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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.netty.handler.ssl.SslContext;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class Cert {

    /**
     * Create Netty SSL context.
     *
     * @return Netty SSL context.
     */
    public abstract SslContext createNettyContext() throws IOException, GeneralSecurityException;

    /**
     * Create Java SSL context.
     *
     * @return Java SSL context.
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
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("RSA");
        kmf.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;

    }
}
