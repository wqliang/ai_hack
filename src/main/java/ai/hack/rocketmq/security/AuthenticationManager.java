package ai.hack.rocketmq.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Manages authentication credentials for RocketMQ broker access.
 * Provides HMAC-based authentication and credential management.
 */
@Component
public class AuthenticationManager {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationManager.class);

    private final String accessKey;
    private final String secretKey;
    private final boolean authenticationEnabled;

    public AuthenticationManager(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.authenticationEnabled = (accessKey != null && secretKey != null);
    }

    /**
     * Checks if authentication is enabled.
     */
    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    /**
     * Gets the access key.
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Generates a signature for authentication.
     */
    public String generateSignature(String data) throws RocketMQSecurityException {
        if (!authenticationEnabled) {
            throw new IllegalStateException("Authentication is not enabled");
        }

        try {
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKeySpec);

            byte[] signatureBytes = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new RocketMQSecurityException("HMAC SHA-256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new RocketMQSecurityException("Invalid secret key for HMAC generation", e);
        }
    }

    /**
     * Creates an authentication header for RocketMQ access.
     */
    public AuthenticationHeader createAuthHeader(String resource, String httpMethod) throws RocketMQSecurityException {
        if (!authenticationEnabled) {
            return AuthenticationHeader.disabled();
        }

        // Create canonical string for signature
        long timestamp = Instant.now().toEpochMilli();
        String canonicalString = String.format("%s\n%s\n%d\n%s",
                httpMethod.toUpperCase(),
                resource,
                timestamp,
                accessKey);

        String signature = generateSignature(canonicalString);

        return new AuthenticationHeader(accessKey, signature, timestamp);
    }

    /**
     * Validates authentication credentials by attempting to generate a test signature.
     */
    public boolean validateCredentials() {
        if (!authenticationEnabled) {
            return true; // No authentication enabled is considered valid
        }

        try {
            String testData = "credential-test-" + Instant.now().toEpochMilli();
            String signature = generateSignature(testData);

            return signature != null && !signature.isEmpty();

        } catch (Exception e) {
            logger.error("Credential validation failed", e);
            return false;
        }
    }

    /**
     * Checks if the credentials are about to expire (for credential rotation).
     */
    public boolean credentialsNeedRotation(long maxAgeMillis) {
        // This would typically check the credential creation timestamp
        // For now, return false - credentials don't have built-in expiration
        return false;
    }

    /**
     * Mask sensitive information for logging.
     */
    public String maskAccessKey() {
        if (!authenticationEnabled || accessKey == null) {
            return "N/A";
        }

        if (accessKey.length() <= 8) {
            return "****";
        }

        return accessKey.substring(0, 4) + "****" + accessKey.substring(accessKey.length() - 4);
    }

    /**
     * Creates a rotated authentication manager (for credential rotation).
     */
    public AuthenticationManager withRotatedCredentials(String newAccessKey, String newSecretKey) {
        return new AuthenticationManager(newAccessKey, newSecretKey);
    }

    /**
     * Authentication header container.
     */
    public static class AuthenticationHeader {
        private final String accessKey;
        private final String signature;
        private final long timestamp;
        private final boolean enabled;

        private AuthenticationHeader(String accessKey, String signature, long timestamp) {
            this.accessKey = accessKey;
            this.signature = signature;
            this.timestamp = timestamp;
            this.enabled = true;
        }

        private AuthenticationHeader() {
            this.accessKey = null;
            this.signature = null;
            this.timestamp = 0L;
            this.enabled = false;
        }

        public static AuthenticationHeader disabled() {
            return new AuthenticationHeader();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public String getSignature() {
            return signature;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /**
         * Gets the authorization header value for HTTP requests.
         */
        public String getHeaderValue() {
            if (!enabled) {
                return null;
            }

            return String.format("RocketMQ %s:%s:%d", accessKey, signature, timestamp);
        }

        @Override
        public String toString() {
            if (!enabled) {
                return "AuthenticationHeader{disabled}";
            }
            return String.format("AuthenticationHeader{accessKey='****', timestamp=%d}", timestamp);
        }
    }
}