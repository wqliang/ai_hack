package ai.hack.rocketmq.client.model;

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Objects;

/**
 * Encapsulates metadata stored in RocketMQ message user properties.
 * <p>
 * This record provides a type-safe wrapper around RocketMQ message properties,
 * handling conversion to/from message user properties for correlation IDs,
 * sender IDs, session IDs, and message types.
 *
 * @param correlationId request-response correlation identifier
 * @param senderId sender's unique identifier
 * @param sessionId session identifier for streaming (null for non-streaming)
 * @param messageType REQUEST or RESPONSE
 * @param timestamp message creation timestamp
 *
 * @author Claude Code
 * @since 1.0.0
 */
public record MessageMetadata(
    String correlationId,
    String senderId,
    String sessionId,
    MessageType messageType,
    long timestamp
) {
    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public MessageMetadata {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");

        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (messageType == MessageType.REQUEST && (senderId == null || senderId.isBlank())) {
            throw new IllegalArgumentException("senderId must not be null/blank for REQUEST messages");
        }
    }

    /**
     * Extracts metadata from a RocketMQ message.
     *
     * @param message the RocketMQ message to extract metadata from
     * @return MessageMetadata instance populated from message properties
     * @throws IllegalArgumentException if required properties are missing
     */
    public static MessageMetadata fromMessage(MessageExt message) {
        String correlationId = message.getUserProperty("correlationId");
        String senderId = message.getUserProperty("senderId");
        String sessionId = message.getUserProperty("sessionId");
        String messageTypeStr = message.getUserProperty("messageType");
        String timestampStr = message.getUserProperty("timestamp");

        if (correlationId == null || messageTypeStr == null || timestampStr == null) {
            throw new IllegalArgumentException("Required message properties missing");
        }

        return new MessageMetadata(
            correlationId,
            senderId,
            sessionId,
            MessageType.valueOf(messageTypeStr),
            Long.parseLong(timestampStr)
        );
    }

    /**
     * Applies this metadata to a RocketMQ message as user properties.
     *
     * @param message the RocketMQ message to populate with metadata
     */
    public void applyToMessage(Message message) {
        message.putUserProperty("correlationId", correlationId);

        if (senderId != null && !senderId.isBlank()) {
            message.putUserProperty("senderId", senderId);
        }

        if (sessionId != null && !sessionId.isBlank()) {
            message.putUserProperty("sessionId", sessionId);
        }

        message.putUserProperty("messageType", messageType.name());
        message.putUserProperty("timestamp", String.valueOf(timestamp));
    }

    /**
     * Enum representing the type of RPC message.
     */
    public enum MessageType {
        /** Request message from sender to receiver */
        REQUEST,

        /** Response message from receiver to sender */
        RESPONSE
    }
}
