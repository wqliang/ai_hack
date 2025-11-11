package ai.hack.rocketmq.client.model;

import java.util.Objects;

/**
 * Represents an RPC request message sent from sender to receiver.
 * <p>
 * This record encapsulates all necessary information for an RPC request including
 * correlation ID for matching responses, sender ID for routing responses back,
 * optional session ID for streaming scenarios, and the actual payload.
 *
 * @param correlationId unique identifier for request-response correlation (UUID format)
 * @param senderId unique identifier of the sender client (UUID format)
 * @param sessionId session identifier for streaming requests (null for non-streaming)
 * @param payload the actual request data (flexible format: text, JSON, binary)
 * @param timestamp Unix epoch timestamp (milliseconds) when request was created
 * @param timeoutMillis timeout duration in milliseconds for this request
 *
 * @author Claude Code
 * @since 1.0.0
 */
public record RpcRequest(
    String correlationId,
    String senderId,
    String sessionId,
    byte[] payload,
    long timestamp,
    long timeoutMillis
) {
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public RpcRequest {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (senderId.isBlank()) {
            throw new IllegalArgumentException("senderId must not be blank");
        }
        if (timeoutMillis <= 0 || timeoutMillis > 300000) {
            throw new IllegalArgumentException("timeoutMillis must be between 1 and 300000");
        }
    }

    /**
     * Checks if this request is part of a streaming session.
     *
     * @return true if sessionId is not null, false otherwise
     */
    public boolean isStreaming() {
        return sessionId != null && !sessionId.isBlank();
    }
}
