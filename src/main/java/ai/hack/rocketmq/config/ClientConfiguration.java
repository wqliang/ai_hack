package ai.hack.rocketmq.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import javax.security.auth.x500.X500Principal;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration class for RocketMQ async client.
 * Supports builder pattern for easy configuration and validation.
 */
public class ClientConfiguration {

    @NotBlank(message = "NameServer address is required")
    private String namesrvAddr;

    private String producerGroup;

    private String consumerGroup;

    @Min(value = 1024, message = "Max message size must be at least 1KB")
    @Max(value = 16 * 1024 * 1024, message = "Max message size must not exceed 16MB")
    private int maxMessageSize = 2 * 1024 * 1024; // 2MB default

    @NotNull(message = "Send timeout is required")
    private Duration sendTimeout = Duration.ofSeconds(3);

    @NotNull(message = "Request timeout is required")
    private Duration requestTimeout = Duration.ofSeconds(5);

    @Min(value = 0, message = "Retry times cannot be negative")
    private int retryTimes = 3;

    @Min(value = 1, message = "Max connections must be at least 1")
    private int maxConnections = 32;

    // Security configuration
    private boolean tlsEnabled = false;
    private String accessKey;
    private String secretKey;
    private String trustStorePath;
    private X500Principal certificatePrincipal;

    // Persistence configuration
    private boolean persistenceEnabled = true;
    private String persistencePath = "./rocketmq-data";
    private Duration persistenceFlushInterval = Duration.ofSeconds(5);

    // Advanced configuration
    private boolean compressionEnabled = true;
    private boolean orderedProcessing = true;
    private int maxConsumeThreads = 64;
    private Duration healthCheckInterval = Duration.ofSeconds(30);

    // Connection pool configuration
    private int connectionPoolSize = 16;
    private Duration connectionMaxIdleTime = Duration.ofMinutes(5);
    private int circuitBreakerThreshold = 10;
    private Duration circuitBreakerTimeout = Duration.ofSeconds(30);
    private int maxConcurrentOperations = 1000;
    private boolean backpressureEnabled = true;
    private double backpressureThreshold = 0.8;

    // Private constructor for builder
    private ClientConfiguration() {}

    // Getters
    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public X500Principal getCertificatePrincipal() {
        return certificatePrincipal;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public String getPersistencePath() {
        return persistencePath;
    }

    public Duration getPersistenceFlushInterval() {
        return persistenceFlushInterval;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public boolean isOrderedProcessing() {
        return orderedProcessing;
    }

    public int getMaxConsumeThreads() {
        return maxConsumeThreads;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public Duration getConnectionMaxIdleTime() {
        return connectionMaxIdleTime;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public Duration getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }

    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    public boolean isBackpressureEnabled() {
        return backpressureEnabled;
    }

    public double getBackpressureThreshold() {
        return backpressureThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ClientConfiguration with fluent API.
     */
    public static class Builder {
        private final ClientConfiguration config = new ClientConfiguration();

        public Builder namesrvAddr(String namesrvAddr) {
            config.namesrvAddr = namesrvAddr;
            return this;
        }

        public Builder producerGroup(String producerGroup) {
            config.producerGroup = producerGroup;
            return this;
        }

        public Builder consumerGroup(String consumerGroup) {
            config.consumerGroup = consumerGroup;
            return this;
        }

        public Builder maxMessageSize(int maxMessageSize) {
            config.maxMessageSize = maxMessageSize;
            return this;
        }

        public Builder sendTimeout(Duration sendTimeout) {
            config.sendTimeout = sendTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            config.requestTimeout = requestTimeout;
            return this;
        }

        public Builder retryTimes(int retryTimes) {
            config.retryTimes = retryTimes;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            config.maxConnections = maxConnections;
            return this;
        }

        public Builder enableTls(boolean enabled) {
            config.tlsEnabled = enabled;
            return this;
        }

        public Builder authentication(String accessKey, String secretKey) {
            config.accessKey = accessKey;
            config.secretKey = secretKey;
            config.tlsEnabled = true; // Auto-enable TLS when credentials provided
            return this;
        }

        public Builder trustStorePath(String trustStorePath) {
            config.trustStorePath = trustStorePath;
            return this;
        }

        public Builder certificatePrincipal(X500Principal principal) {
            config.certificatePrincipal = principal;
            return this;
        }

        public Builder persistence(String path, Duration interval) {
            config.persistencePath = path;
            config.persistenceFlushInterval = interval;
            config.persistenceEnabled = true;
            return this;
        }

        public Builder enablePersistence(boolean enabled) {
            config.persistenceEnabled = enabled;
            return this;
        }

        public Builder compressionEnabled(boolean enabled) {
            config.compressionEnabled = enabled;
            return this;
        }

        public Builder orderedProcessing(boolean enabled) {
            config.orderedProcessing = enabled;
            return this;
        }

        public Builder maxConsumeThreads(int maxConsumeThreads) {
            config.maxConsumeThreads = maxConsumeThreads;
            return this;
        }

        public Builder healthCheckInterval(Duration healthCheckInterval) {
            config.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public Builder connectionPoolSize(int connectionPoolSize) {
            config.connectionPoolSize = Math.max(1, connectionPoolSize);
            return this;
        }

        public Builder connectionMaxIdleTime(Duration maxIdleTime) {
            config.connectionMaxIdleTime = maxIdleTime;
            return this;
        }

        public Builder circuitBreakerThreshold(int threshold) {
            config.circuitBreakerThreshold = Math.max(1, threshold);
            return this;
        }

        public Builder circuitBreakerTimeout(Duration timeout) {
            config.circuitBreakerTimeout = timeout;
            return this;
        }

        public Builder maxConcurrentOperations(int maxConcurrentOperations) {
            config.maxConcurrentOperations = Math.max(1, maxConcurrentOperations);
            return this;
        }

        public Builder enableBackpressure(boolean enabled) {
            config.backpressureEnabled = enabled;
            return this;
        }

        public Builder backpressureThreshold(double threshold) {
            config.backpressureThreshold = Math.max(0.1, Math.min(1.0, threshold));
            return this;
        }

        public ClientConfiguration build() {
            validate();
            return config;
        }

        private void validate() {
            Objects.requireNonNull(config.namesrvAddr, "NameServer address is required");

            if (config.tlsEnabled && (config.accessKey == null || config.secretKey == null)) {
                throw new IllegalArgumentException("TLS enabled requires access key and secret key");
            }

            if (config.persistenceEnabled && config.persistencePath == null) {
                throw new IllegalArgumentException("Persistence enabled requires a persistence path");
            }

            // Auto-generate group names if not provided
            if (config.producerGroup == null) {
                config.producerGroup = "rocketmq-producer-" + System.currentTimeMillis();
            }
            if (config.consumerGroup == null) {
                config.consumerGroup = "rocketmq-consumer-" + System.currentTimeMillis();
            }
        }
    }

    @Override
    public String toString() {
        return "ClientConfiguration{" +
                "namesrvAddr='" + namesrvAddr + '\'' +
                ", producerGroup='" + producerGroup + '\'' +
                ", consumerGroup='" + consumerGroup + '\'' +
                ", maxMessageSize=" + maxMessageSize +
                ", sendTimeout=" + sendTimeout +
                ", requestTimeout=" + requestTimeout +
                ", retryTimes=" + retryTimes +
                ", maxConnections=" + maxConnections +
                ", tlsEnabled=" + tlsEnabled +
                ", persistenceEnabled=" + persistenceEnabled +
                ", compressionEnabled=" + compressionEnabled +
                ", orderedProcessing=" + orderedProcessing +
                ", maxConsumeThreads=" + maxConsumeThreads +
                ", connectionPoolSize=" + connectionPoolSize +
                ", connectionMaxIdleTime=" + connectionMaxIdleTime +
                ", circuitBreakerThreshold=" + circuitBreakerThreshold +
                ", circuitBreakerTimeout=" + circuitBreakerTimeout +
                ", maxConcurrentOperations=" + maxConcurrentOperations +
                ", backpressureEnabled=" + backpressureEnabled +
                ", backpressureThreshold=" + backpressureThreshold +
                '}';
    }
}