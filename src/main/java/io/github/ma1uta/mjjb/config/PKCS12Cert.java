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
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * PKCS12 certificate info.
 */
@JsonTypeName("pkcs12")
public class PKCS12Cert extends Cert {

    @JsonProperty("key_store")
    private String keyStore;

    @JsonProperty("key_store_password")
    private String keyStorePassword;

    @Override
    public SslContext createNettyContext() throws IOException, GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("RSA");
        kmf.init(loadKeyStore(), getKeyStorePassword().toCharArray());
        return SslContextBuilder.forServer(kmf).build();
    }

    @Override
    public SSLContext createJavaContext() throws IOException, GeneralSecurityException {
        return load(loadKeyStore(), getKeyStorePassword().toCharArray());
    }

    private KeyStore loadKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(Files.newInputStream(Paths.get(getKeyStore())), getKeyStorePassword().toCharArray());
        return keyStore;
    }

    @Override
    public CertificateType getType() {
        return CertificateType.PKCS12;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }
}
