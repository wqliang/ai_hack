package ai.hack.rocketmq.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Configuration for TLS (Transport Layer Security) in RocketMQ client.
 * Provides SSL context creation and certificate validation.
 */
@Component
public class TLSConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TLSConfiguration.class);

    private final boolean tlsEnabled;
    private final String trustStorePath;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String trustStorePassword;

    private SSLContext sslContext;

    public TLSConfiguration(boolean tlsEnabled, String trustStorePath, String keyStorePath,
                           String keyStorePassword, String trustStorePassword) {
        this.tlsEnabled = tlsEnabled;
        this.trustStorePath = trustStorePath;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * Initializes the TLS configuration.
     */
    public void initialize() throws RocketMQSecurityException {
        if (!tlsEnabled) {
            logger.info("TLS is disabled");
            return;
        }

        try {
            sslContext = createSSLContext();
            logger.info("TLS configuration initialized successfully");
        } catch (Exception e) {
            throw new RocketMQSecurityException("Failed to initialize TLS", e);
        }
    }

    /**
     * Gets the SSL context for TLS connections.
     */
    public SSLContext getSSLContext() {
        if (!tlsEnabled) {
            throw new IllegalStateException("TLS is not enabled");
        }
        return sslContext;
    }

    /**
     * Checks if TLS is enabled.
     */
    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    private SSLContext createSSLContext() throws Exception {
        KeyManagerFactory kmf = null;
        TrustManagerFactory tmf = null;

        // Initialize KeyManager if key store is provided
        if (keyStorePath != null && !keyStorePath.isEmpty()) {
            KeyStore keyStore = loadKeyStore(keyStorePath, keyStorePassword);
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyStorePassword.toCharArray());
            logger.debug("KeyStore loaded from: {}", keyStorePath);
        }

        // Initialize TrustManager
        if (trustStorePath != null && !trustStorePath.isEmpty()) {
            KeyStore trustStore = loadKeyStore(trustStorePath, trustStorePassword);
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            logger.debug("TrustStore loaded from: {}", trustStorePath);
        } else {
            // Use default trust store (system certificates)
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            logger.debug("Using default system trust store");
        }

        // Create SSL context
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf != null ? kmf.getKeyManagers() : null,
                     tmf != null ? tmf.getTrustManagers() : createDefaultTrustManagers(),
                     new SecureRandom());

        return context;
    }

    private KeyStore loadKeyStore(String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        }

        return keyStore;
    }

    private TrustManager[] createDefaultTrustManagers() {
        return new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    logger.debug("Client certificate validation: {}", authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    logger.debug("Server certificate validation: {}", authType);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
    }

    /**
     * Creates an SSL socket factory for TLS connections.
     */
    public SSLSocketFactory createSSLSocketFactory() throws RocketMQSecurityException {
        if (!tlsEnabled) {
            throw new IllegalStateException("TLS is not enabled");
        }

        return sslContext.getSocketFactory();
    }

    /**
     * Creates a hostname verifier that's appropriate for the security level.
     */
    public HostnameVerifier createHostnameVerifier(boolean strictHostnameVerification) {
        if (strictHostnameVerification) {
            return HttpsURLConnection.getDefaultHostnameVerifier();
        } else {
            return (hostname, session) -> {
                logger.warn("Hostname verification disabled - accepting: {}", hostname);
                return true;
            };
        }
    }

    /**
     * Validates a certificate chain.
     */
    public boolean validateCertificateChain(X509Certificate[] chain, String authType) {
        try {
            // Basic validation - in production, implement comprehensive certificate validation
            if (chain == null || chain.length == 0) {
                logger.error("Certificate chain is empty");
                return false;
            }

            X509Certificate cert = chain[0];

            // Check if certificate is expired
            cert.checkValidity();

            // Log certificate information
            logger.debug("Certificate validated - Subject: {}, Issuer: {}, Valid until: {}",
                        cert.getSubjectX500Principal(),
                        cert.getIssuerX500Principal(),
                        cert.getNotAfter());

            return true;

        } catch (Exception e) {
            logger.error("Certificate validation failed", e);
            return false;
        }
    }
}