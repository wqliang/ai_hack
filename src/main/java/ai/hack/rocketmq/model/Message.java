package ai.hack.rocketmq.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a RocketMQ message with all required metadata.
 * Supports builder pattern for easy message construction.
 */
public class Message {

    private String messageId;
    private String topic;
    private String callbackTopic;
    private Map<String, Object> headers;
    private byte[] payload;
    private Instant timestamp;
    private MessagePriority priority;
    private Set<String> tags;
    private MessageStatus status;

    // Private constructor for builder
    private Message() {
        this.headers = new HashMap<>();
        this.tags = new HashSet<>();
        this.timestamp = Instant.now();
        this.priority = MessagePriority.NORMAL;
        this.status = MessageStatus.PENDING;
    }

    // Constructor for backwards compatibility
    public Message(String topic, byte[] payload) {
        this();
        this.topic = topic;
        this.payload = payload;
        this.messageId = generateMessageId();
    }

    public Message(String topic, byte[] payload, String callbackTopic) {
        this(topic, payload);
        this.callbackTopic = callbackTopic;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters and setters
    public String getMessageId() {
        return messageId;
    }

    public String getTopic() {
        return topic;
    }

    public String getCallbackTopic() {
        return callbackTopic;
    }

    public Map<String, Object> getHeaders() {
        return new HashMap<>(headers);
    }

    public byte[] getPayload() {
        return payload.clone(); // Defensive copy
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public MessagePriority getPriority() {
        return priority;
    }

    public Set<String> getTags() {
        return new HashSet<>(tags);
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setCallbackTopic(String callbackTopic) {
        this.callbackTopic = callbackTopic;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload != null ? payload.clone() : null;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setPriority(MessagePriority priority) {
        this.priority = priority != null ? priority : MessagePriority.NORMAL;
    }

    public void setStatus(MessageStatus status) {
        this.status = status != null ? status : MessageStatus.PENDING;
    }

    // Utility methods
    public String getHeader(String key) {
        return headers.get(key) != null ? headers.get(key).toString() : null;
    }

    public Message withHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }

    public Message withTag(String tag) {
        tags.add(tag);
        return this;
    }

    public Message withTag(String... tags) {
        for (String tag : tags) {
            this.tags.add(tag);
        }
        return this;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public int getPayloadSize() {
        return payload != null ? payload.length : 0;
    }

    /**
     * Checks if this is a request-response message (has callback topic).
     */
    public boolean isRequestResponse() {
        return callbackTopic != null && !callbackTopic.trim().isEmpty();
    }

    /**
     * Creates a response message for this request.
     */
    public Message createResponse(byte[] responsePayload) {
        if (!isRequestResponse()) {
            throw new IllegalStateException("Cannot create response for message without callback topic");
        }

        return Message.builder()
                .topic(callbackTopic)
                .payload(responsePayload)
                .header("response-to", messageId)
                .build();
    }

    /**
     * Calculates the approximate size of this message including metadata.
     */
    public int getTotalSize() {
        int size = getPayloadSize();

        // Add metadata overhead (rough estimation)
        size += messageId != null ? messageId.length() : 0;
        size += topic != null ? topic.length() : 0;
        size += callbackTopic != null ? callbackTopic.length() : 0;

        // Add headers size
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            size += entry.getKey().length() + entry.getValue().toString().length() + 8; // rough overhead
        }

        // Add tags size
        for (String tag : tags) {
            size += tag.length() + 4; // rough overhead
        }

        return size;
    }

    private static String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String toString() {
        return String.format("Message{id='%s', topic='%s', status=%s, priority=%s, payloadSize=%d, " +
                           "hasCallback=%s, headerCount=%d, tagCount=%d}",
                           messageId, topic, status, priority, getPayloadSize(),
                           isRequestResponse(), headers.size(), tags.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;
        return messageId != null ? messageId.equals(message.messageId) : message.messageId == null;
    }

    @Override
    public int hashCode() {
        return messageId != null ? messageId.hashCode() : 0;
    }

    /**
     * Builder for Message with fluent API.
     */
    public static class Builder {
        private final Message message = new Message();

        public Builder messageId(String messageId) {
            message.messageId = messageId;
            return this;
        }

        public Builder topic(String topic) {
            message.topic = topic;
            return this;
        }

        public Builder payload(byte[] payload) {
            message.payload = payload != null ? payload.clone() : null;
            return this;
        }

        public Builder payload(String payload) {
            message.payload = payload != null ? payload.getBytes() : null;
            return this;
        }

        public Builder callbackTopic(String callbackTopic) {
            message.callbackTopic = callbackTopic;
            return this;
        }

        public Builder header(String key, Object value) {
            message.headers.put(key, value);
            return this;
        }

        public Builder headers(Map<String, Object> headers) {
            if (headers != null) {
                message.headers.putAll(headers);
            }
            return this;
        }

        public Builder priority(MessagePriority priority) {
            message.priority = priority;
            return this;
        }

        public Builder priority(String priority) {
            try {
                message.priority = MessagePriority.valueOf(priority.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid priority: " + priority, e);
            }
            return this;
        }

        public Builder tag(String tag) {
            message.tags.add(tag);
            return this;
        }

        public Builder tags(Set<String> tags) {
            if (tags != null) {
                message.tags.addAll(tags);
            }
            return this;
        }

        public Builder status(MessageStatus status) {
            message.status = status;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            message.timestamp = timestamp;
            return this;
        }

        public Message build() {
            validate();
            if (message.messageId == null) {
                message.messageId = generateMessageId();
            }
            return message;
        }

        private void validate() {
            if (message.topic == null || message.topic.trim().isEmpty()) {
                throw new IllegalArgumentException("Topic is required");
            }
            if (message.payload == null) {
                throw new IllegalArgumentException("Payload is required");
            }
            if (message.headers == null) {
                message.headers = new HashMap<>();
            }
            if (message.tags == null) {
                message.tags = new HashSet<>();
            }
            if (message.priority == null) {
                message.priority = MessagePriority.NORMAL;
            }
            if (message.status == null) {
                message.status = MessageStatus.PENDING;
            }
            if (message.timestamp == null) {
                message.timestamp = Instant.now();
            }
        }
    }
}