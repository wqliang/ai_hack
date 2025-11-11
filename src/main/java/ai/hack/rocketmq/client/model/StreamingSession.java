package ai.hack.rocketmq.client.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents metadata for an active streaming session.
 * <p>
 * A streaming session allows multiple request messages to be sent sequentially
 * with the same session ID, ensuring all messages are processed by the same
 * receiver (routed to the same message queue). The session ends with a single
 * aggregated response.
 * <p>
 * This record tracks session state, timestamps, and correlation ID for the
 * final response.
 *
 * @param sessionId unique identifier for this streaming session (UUID format)
 * @param senderId the sender ID associated with this session
 * @param correlationId correlation ID for the final response
 * @param createdAt timestamp when session was created
 * @param lastActivityAt timestamp of last activity (message sent)
 * @param messageCount number of messages sent in this session
 * @param active whether this session is currently active
 *
 * @author Claude Code
 * @since 1.0.0
 */
public record StreamingSession(
    String sessionId,
    String senderId,
    String correlationId,
    Instant createdAt,
    Instant lastActivityAt,
    int messageCount,
    boolean active
) {
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public StreamingSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");

        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (senderId.isBlank()) {
            throw new IllegalArgumentException("senderId must not be blank");
        }
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount must not be negative");
        }
    }

    /**
     * Creates a new active streaming session.
     *
     * @param sessionId unique session identifier
     * @param senderId sender identifier
     * @param correlationId correlation ID for final response
     * @return new active StreamingSession
     */
    public static StreamingSession create(String sessionId, String senderId, String correlationId) {
        Instant now = Instant.now();
        return new StreamingSession(sessionId, senderId, correlationId, now, now, 0, true);
    }

    /**
     * Creates a copy of this session with updated activity timestamp and incremented message count.
     *
     * @return new StreamingSession with updated lastActivityAt and messageCount
     */
    public StreamingSession withActivity() {
        return new StreamingSession(
            sessionId,
            senderId,
            correlationId,
            createdAt,
            Instant.now(),
            messageCount + 1,
            active
        );
    }

    /**
     * Creates a copy of this session marked as inactive.
     *
     * @return new StreamingSession with active = false
     */
    public StreamingSession deactivate() {
        return new StreamingSession(
            sessionId,
            senderId,
            correlationId,
            createdAt,
            lastActivityAt,
            messageCount,
            false
        );
    }

    /**
     * Checks if this session has been idle for longer than the specified timeout.
     *
     * @param timeoutMillis idle timeout in milliseconds
     * @return true if session has been idle longer than timeout
     */
    public boolean isIdleTimeout(long timeoutMillis) {
        long idleMillis = Instant.now().toEpochMilli() - lastActivityAt.toEpochMilli();
        return idleMillis > timeoutMillis;
    }

    /**
     * Gets the age of this session in milliseconds.
     *
     * @return session age in milliseconds
     */
    public long getAgeMillis() {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }
}
