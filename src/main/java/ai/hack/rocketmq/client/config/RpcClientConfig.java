package ai.hack.rocketmq.client.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for RocketMQ RPC Client.
 * <p>
 * This class binds configuration from application.yml under the prefix
 * "rocketmq.rpc.client" and provides type-safe access to RPC client settings.
 *
 * @author Claude Code
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "rocketmq.rpc.client")
@Validated
public class RpcClientConfig {

    /**
     * RocketMQ broker address (name server address).
     * Format: host:port (e.g., localhost:9876)
     */
    @NotBlank(message = "Broker URL must not be blank")
    private String brokerUrl = "localhost:9876";

    /**
     * Topic for sending RPC request messages.
     * All senders publish to this shared topic.
     */
    @NotBlank(message = "Request topic must not be blank")
    private String requestTopic = "RPC_REQUEST";

    /**
     * Default timeout in milliseconds for RPC calls.
     * Applies when no explicit timeout is specified.
     */
    @Min(value = 100, message = "Default timeout must be at least 100ms")
    @Max(value = 300000, message = "Default timeout must not exceed 300000ms (5 minutes)")
    private long defaultTimeoutMillis = 30000;

    /**
     * Maximum number of concurrent pending requests allowed.
     * Prevents resource exhaustion from too many in-flight requests.
     */
    @Min(value = 1, message = "Max concurrent requests must be at least 1")
    @Max(value = 10000, message = "Max concurrent requests must not exceed 10000")
    private int maxConcurrentRequests = 1000;

    /**
     * Maximum number of concurrent streaming sessions allowed.
     * Prevents resource exhaustion from too many active sessions.
     */
    @Min(value = 1, message = "Max concurrent sessions must be at least 1")
    @Max(value = 1000, message = "Max concurrent sessions must not exceed 1000")
    private int maxConcurrentSessions = 100;

    /**
     * Prefix for response topic names.
     * Actual topic name will be: prefix + senderId (e.g., RESPONSE_uuid)
     */
    @NotBlank(message = "Response topic prefix must not be blank")
    private String responseTopicPrefix = "RESPONSE_";

    // ========== Producer Performance Settings ==========

    /**
     * Timeout for sending messages in milliseconds.
     * Determines how long to wait for send acknowledgment from broker.
     */
    @Min(value = 1000, message = "Send message timeout must be at least 1000ms")
    @Max(value = 30000, message = "Send message timeout must not exceed 30000ms")
    private int sendMsgTimeout = 5000;

    /**
     * Number of retry attempts for synchronous send failures.
     * Higher values increase reliability but may impact latency.
     */
    @Min(value = 0, message = "Retry times must be at least 0")
    @Max(value = 10, message = "Retry times must not exceed 10")
    private int retryTimesWhenSendFailed = 2;

    /**
     * Number of retry attempts for asynchronous send failures.
     * Applies to async send operations.
     */
    @Min(value = 0, message = "Async retry times must be at least 0")
    @Max(value = 10, message = "Async retry times must not exceed 10")
    private int retryTimesWhenSendAsyncFailed = 2;

    /**
     * Maximum message size in bytes.
     * Messages exceeding this size will be rejected.
     */
    @Min(value = 1024, message = "Max message size must be at least 1KB")
    @Max(value = 4194304, message = "Max message size must not exceed 4MB")
    private int maxMessageSize = 4194304; // 4MB

    /**
     * Message body size threshold for compression (in bytes).
     * Messages larger than this will be compressed to reduce network traffic.
     */
    @Min(value = 1024, message = "Compress threshold must be at least 1KB")
    @Max(value = 4194304, message = "Compress threshold must not exceed 4MB")
    private int compressMsgBodyOverHowMuch = 4096; // 4KB

    // ========== Consumer Performance Settings ==========

    /**
     * Minimum number of threads for consuming messages.
     * Lower bound for thread pool size.
     */
    @Min(value = 1, message = "Min consume threads must be at least 1")
    @Max(value = 100, message = "Min consume threads must not exceed 100")
    private int consumeThreadMin = 4;

    /**
     * Maximum number of threads for consuming messages.
     * Upper bound for thread pool size, allows scaling under load.
     */
    @Min(value = 1, message = "Max consume threads must be at least 1")
    @Max(value = 1000, message = "Max consume threads must not exceed 1000")
    private int consumeThreadMax = 16;

    /**
     * Number of messages to pull from broker in one batch.
     * Higher values improve throughput but increase memory usage.
     */
    @Min(value = 1, message = "Pull batch size must be at least 1")
    @Max(value = 100, message = "Pull batch size must not exceed 100")
    private int pullBatchSize = 32;

    /**
     * Maximum number of messages to consume in one batch.
     * Affects message processing concurrency.
     */
    @Min(value = 1, message = "Consume batch size must be at least 1")
    @Max(value = 100, message = "Consume batch size must not exceed 100")
    private int consumeMessageBatchMaxSize = 1;

    // ========== Timeout Executor Settings ==========

    /**
     * Thread pool size for timeout management.
     * Handles scheduling of request timeout tasks.
     */
    @Min(value = 1, message = "Timeout executor threads must be at least 1")
    @Max(value = 10, message = "Timeout executor threads must not exceed 10")
    private int timeoutExecutorThreadPoolSize = 2;

    // ========== Monitoring Settings ==========

    /**
     * Enable performance metrics logging.
     * When enabled, metrics summary will be logged periodically.
     */
    private boolean metricsLoggingEnabled = false;

    /**
     * Interval in seconds for logging performance metrics.
     * Only used when metricsLoggingEnabled is true.
     */
    @Min(value = 10, message = "Metrics logging interval must be at least 10 seconds")
    @Max(value = 3600, message = "Metrics logging interval must not exceed 3600 seconds")
    private int metricsLoggingIntervalSeconds = 60;

    // Getters and Setters

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getRequestTopic() {
        return requestTopic;
    }

    public void setRequestTopic(String requestTopic) {
        this.requestTopic = requestTopic;
    }

    public long getDefaultTimeoutMillis() {
        return defaultTimeoutMillis;
    }

    public void setDefaultTimeoutMillis(long defaultTimeoutMillis) {
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public void setMaxConcurrentSessions(int maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public String getResponseTopicPrefix() {
        return responseTopicPrefix;
    }

    public void setResponseTopicPrefix(String responseTopicPrefix) {
        this.responseTopicPrefix = responseTopicPrefix;
    }

    public int getSendMsgTimeout() {
        return sendMsgTimeout;
    }

    public void setSendMsgTimeout(int sendMsgTimeout) {
        this.sendMsgTimeout = sendMsgTimeout;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public int getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(int retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getCompressMsgBodyOverHowMuch() {
        return compressMsgBodyOverHowMuch;
    }

    public void setCompressMsgBodyOverHowMuch(int compressMsgBodyOverHowMuch) {
        this.compressMsgBodyOverHowMuch = compressMsgBodyOverHowMuch;
    }

    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public int getPullBatchSize() {
        return pullBatchSize;
    }

    public void setPullBatchSize(int pullBatchSize) {
        this.pullBatchSize = pullBatchSize;
    }

    public int getConsumeMessageBatchMaxSize() {
        return consumeMessageBatchMaxSize;
    }

    public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
        this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
    }

    public int getTimeoutExecutorThreadPoolSize() {
        return timeoutExecutorThreadPoolSize;
    }

    public void setTimeoutExecutorThreadPoolSize(int timeoutExecutorThreadPoolSize) {
        this.timeoutExecutorThreadPoolSize = timeoutExecutorThreadPoolSize;
    }

    public boolean isMetricsLoggingEnabled() {
        return metricsLoggingEnabled;
    }

    public void setMetricsLoggingEnabled(boolean metricsLoggingEnabled) {
        this.metricsLoggingEnabled = metricsLoggingEnabled;
    }

    public int getMetricsLoggingIntervalSeconds() {
        return metricsLoggingIntervalSeconds;
    }

    public void setMetricsLoggingIntervalSeconds(int metricsLoggingIntervalSeconds) {
        this.metricsLoggingIntervalSeconds = metricsLoggingIntervalSeconds;
    }
}
