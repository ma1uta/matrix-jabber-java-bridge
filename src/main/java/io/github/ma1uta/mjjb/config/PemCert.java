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
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

@JsonTypeName("pem")
public class PemCert extends Cert {

    private static final Pattern DELIMITER_PATTERN = Pattern.compile("(?m)(?s)^---*BEGIN ([^-]+)---*$([^-]+)^---*END[^-]+-+$");

    @JsonProperty("key")
    private String keyFile;

    @JsonProperty("chain")
    private String keyCertChainFile;

    @Override
    public SslContext createNettyContext() throws IOException, GeneralSecurityException {
        CertAndPrivKey certAndPrivKey = readBothFiles();
        PrivateKey privateKey = certAndPrivKey.getPrivateKey();
        List<X509Certificate> certificates = certAndPrivKey.getCertificates();

        return SslContextBuilder.forServer(privateKey, certificates.toArray(new X509Certificate[0])).build();
    }

    @Override
    public SSLContext createJavaContext() throws IOException, GeneralSecurityException {
        CertAndPrivKey certAndPrivKey = readBothFiles();
        PrivateKey privateKey = certAndPrivKey.getPrivateKey();
        List<X509Certificate> certificates = certAndPrivKey.getCertificates();

        if (privateKey == null) {
            throw new RuntimeException("RSA private key not found in PEM file");
        }

        char[] keyStorePassword = new char[0];

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        int count = 0;
        for (Certificate cert : certificates) {
            keyStore.setCertificateEntry("cert" + count, cert);
            count++;
        }
        Certificate[] chain = certificates.toArray(new Certificate[0]);
        keyStore.setKeyEntry("key", privateKey, keyStorePassword, chain);

        return load(keyStore, keyStorePassword);
    }

    private CertAndPrivKey readBothFiles() throws IOException, GeneralSecurityException {
        CertAndPrivKey privKeyTuple = readPem(Paths.get(getKeyFile()));
        CertAndPrivKey keyCertChainTuple = readPem(Paths.get(getKeyCertChainFile()));

        PrivateKey privateKey = privKeyTuple.getPrivateKey();
        List<X509Certificate> certificates = keyCertChainTuple.getCertificates();
        certificates.addAll(privKeyTuple.getCertificates());

        return new CertAndPrivKey(privateKey, certificates);
    }

    @Override
    public CertificateType getType() {
        return CertificateType.PEM;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getKeyCertChainFile() {
        return keyCertChainFile;
    }

    public void setKeyCertChainFile(String keyCertChainFile) {
        this.keyCertChainFile = keyCertChainFile;
    }

    protected CertAndPrivKey readPem(Path path) throws IOException, GeneralSecurityException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + path.toString());
        }
        if (!Files.isReadable(path)) {
            throw new FileNotFoundException("Cannot read the file: " + path.toString());
        }

        String content = String.join("", Files.readAllLines(path, StandardCharsets.UTF_8)).replaceAll("\n", "");

        Matcher m = DELIMITER_PATTERN.matcher(content);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        Base64.Decoder decoder = Base64.getMimeDecoder();
        List<X509Certificate> certList = new ArrayList<>();

        PrivateKey privateKey = null;

        int start = 0;
        while (m.find(start)) {
            String type = m.group(1);
            String base64Data = m.group(2);
            byte[] data = decoder.decode(base64Data);
            start += m.group(0).length();
            type = type.toUpperCase();
            if (type.contains("CERTIFICATE")) {
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(data));
                certList.add(cert);
            } else if (type.contains("RSA PRIVATE KEY")) {

                DerInputStream derReader = new DerInputStream(Base64.getDecoder().decode(data));

                DerValue[] seq = derReader.getSequence(0);

                if (seq.length < 9) {
                    throw new GeneralSecurityException("Could not parse a PKCS1 private key.");
                }

                // skip version seq[0];
                BigInteger modulus = seq[1].getBigInteger();
                BigInteger publicExp = seq[2].getBigInteger();
                BigInteger privateExp = seq[3].getBigInteger();
                BigInteger prime1 = seq[4].getBigInteger();
                BigInteger prime2 = seq[5].getBigInteger();
                BigInteger exp1 = seq[6].getBigInteger();
                BigInteger exp2 = seq[7].getBigInteger();
                BigInteger crtCoef = seq[8].getBigInteger();

                RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2,
                    crtCoef);

                KeyFactory factory = KeyFactory.getInstance("RSA");

                privateKey = factory.generatePrivate(keySpec);
            } else {
                System.err.println("Unsupported type: " + type);
            }

        }
        return new CertAndPrivKey(privateKey, certList);
    }
}
