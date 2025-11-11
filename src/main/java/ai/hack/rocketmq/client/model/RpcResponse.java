package ai.hack.rocketmq.client.model;

import java.util.Objects;

/**
 * Represents an RPC response message sent from receiver back to sender.
 * <p>
 * This record contains the response payload, success status, and optional error
 * message. The correlation ID matches the original request for proper routing.
 *
 * @param correlationId matches the correlationId from the corresponding RpcRequest
 * @param payload the response data
 * @param timestamp Unix epoch timestamp (milliseconds) when response was created
 * @param success indicates if the request was processed successfully
 * @param errorMessage error description if success = false (null if successful)
 *
 * @author Claude Code
 * @since 1.0.0
 */
public record RpcResponse(
    String correlationId,
    byte[] payload,
    long timestamp,
    boolean success,
    String errorMessage
) {
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public RpcResponse {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("errorMessage must be provided when success = false");
        }
    }

    /**
     * Creates a successful RPC response.
     *
     * @param correlationId the correlation ID from the request
     * @param payload the response payload
     * @return a new successful RpcResponse
     */
    public static RpcResponse success(String correlationId, byte[] payload) {
        return new RpcResponse(correlationId, payload, System.currentTimeMillis(), true, null);
    }

    /**
     * Creates an error RPC response.
     *
     * @param correlationId the correlation ID from the request
     * @param errorMessage the error description
     * @return a new error RpcResponse with empty payload
     */
    public static RpcResponse error(String correlationId, String errorMessage) {
        return new RpcResponse(correlationId, new byte[0], System.currentTimeMillis(), false, errorMessage);
    }
}
