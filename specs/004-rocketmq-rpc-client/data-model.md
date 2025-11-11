# Data Model: RocketMQ RPC Client Wrapper

**Feature**: 004-rocketmq-rpc-client
**Date**: 2025-11-11
**Purpose**: Define the data structures for RPC messages, sessions, and metadata

## Core Entities

### RpcRequest

Represents a request message sent from sender to receiver.

**Attributes**:
- `correlationId`: String (UUID) - Unique identifier for request-response correlation
- `senderId`: String (UUID) - Unique identifier of the sender client
- `sessionId`: String (Optional) - Session identifier for streaming requests (null for non-streaming)
- `payload`: byte[] - The actual request data (flexible format: text, JSON, binary)
- `timestamp`: long - Unix epoch timestamp (milliseconds) when request was created
- `timeoutMillis`: long - Timeout duration in milliseconds for this request

**Relationships**:
- One RpcRequest correlates to zero or one RpcResponse (zero if timeout/error)
- Multiple RpcRequests may share the same sessionId (streaming scenario)
- Each RpcRequest belongs to exactly one sender (identified by senderId)

**Validation Rules**:
- correlationId must be non-null and non-empty
- senderId must be non-null and non-empty
- payload must be non-null (can be empty array)
- timeoutMillis must be > 0 and <= 300000 (5 minutes max)
- sessionId can be null for non-streaming requests

**State Transitions**:
```
Created → Sent → [Response Received | Timeout | Error]
```

**Java Implementation**:
```java
public record RpcRequest(
    String correlationId,
    String senderId,
    String sessionId,  // nullable
    byte[] payload,
    long timestamp,
    long timeoutMillis
) {
    public RpcRequest {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (timeoutMillis <= 0 || timeoutMillis > 300000) {
            throw new IllegalArgumentException("timeoutMillis must be between 1 and 300000");
        }
    }

    public boolean isStreaming() {
        return sessionId != null;
    }
}
```

---

### RpcResponse

Represents a response message sent from receiver back to sender.

**Attributes**:
- `correlationId`: String (UUID) - Matches the correlationId from the corresponding RpcRequest
- `payload`: byte[] - The response data
- `timestamp`: long - Unix epoch timestamp (milliseconds) when response was created
- `success`: boolean - Indicates if the request was processed successfully
- `errorMessage`: String (Optional) - Error description if success = false

**Relationships**:
- One RpcResponse correlates to exactly one RpcRequest (via correlationId)
- RpcResponse is sent to the sender's dedicated response topic

**Validation Rules**:
- correlationId must be non-null and non-empty
- payload must be non-null (can be empty for error responses)
- errorMessage should be null if success = true, non-null if success = false

**State Transitions**:
```
Created → Sent → Received by Sender
```

**Java Implementation**:
```java
public record RpcResponse(
    String correlationId,
    byte[] payload,
    long timestamp,
    boolean success,
    String errorMessage  // nullable
) {
    public RpcResponse {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (!success && errorMessage == null) {
            throw new IllegalArgumentException("errorMessage must be provided when success = false");
        }
    }

    public static RpcResponse success(String correlationId, byte[] payload) {
        return new RpcResponse(correlationId, payload, System.currentTimeMillis(), true, null);
    }

    public static RpcResponse error(String correlationId, String errorMessage) {
        return new RpcResponse(correlationId, new byte[0], System.currentTimeMillis(), false, errorMessage);
    }
}
```

---

### StreamingSession

Represents metadata for a streaming session where multiple requests/responses are exchanged.

**Attributes**:
- `sessionId`: String (UUID) - Unique identifier for the session
- `senderId`: String (UUID) - The sender participating in this session
- `requestTopic`: String - Topic where requests are sent
- `responseTopic`: String - Topic where responses are received
- `createdAt`: long - Unix epoch timestamp when session was created
- `lastActivityAt`: long - Unix epoch timestamp of last message sent/received
- `messageCount`: int - Number of messages sent in this session
- `active`: boolean - Whether the session is currently active

**Relationships**:
- One StreamingSession contains multiple RpcRequests (all sharing the sessionId)
- One StreamingSession may have multiple RpcResponses
- Each StreamingSession belongs to exactly one sender

**Validation Rules**:
- sessionId must be non-null and non-empty
- senderId must be non-null and non-empty
- requestTopic and responseTopic must be non-null
- messageCount must be >= 0
- lastActivityAt must be >= createdAt

**State Transitions**:
```
Created → Active → [Completed | Aborted | Timeout]
```

**Java Implementation**:
```java
public record StreamingSession(
    String sessionId,
    String senderId,
    String requestTopic,
    String responseTopic,
    long createdAt,
    long lastActivityAt,
    int messageCount,
    boolean active
) {
    public StreamingSession {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(requestTopic, "requestTopic must not be null");
        Objects.requireNonNull(responseTopic, "responseTopic must not be null");
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount must be >= 0");
        }
        if (lastActivityAt < createdAt) {
            throw new IllegalArgumentException("lastActivityAt must be >= createdAt");
        }
    }

    public StreamingSession recordActivity() {
        return new StreamingSession(
            sessionId, senderId, requestTopic, responseTopic,
            createdAt, System.currentTimeMillis(), messageCount + 1, active
        );
    }

    public StreamingSession close() {
        return new StreamingSession(
            sessionId, senderId, requestTopic, responseTopic,
            createdAt, System.currentTimeMillis(), messageCount, false
        );
    }
}
```

---

### MessageMetadata

Encapsulates metadata stored in RocketMQ message user properties.

**Attributes**:
- `correlationId`: String - Request-response correlation identifier
- `senderId`: String - Sender's unique identifier
- `sessionId`: String (Optional) - Session identifier for streaming
- `messageType`: MessageType (enum) - REQUEST or RESPONSE
- `timestamp`: long - Message creation timestamp

**Relationships**:
- MessageMetadata is extracted from/embedded into RocketMQ Message user properties
- Each RocketMQ Message has exactly one MessageMetadata

**Validation Rules**:
- correlationId must be non-null and non-empty
- senderId must be non-null and non-empty for REQUEST messages
- messageType must be non-null
- sessionId can be null for non-streaming messages

**Java Implementation**:
```java
public record MessageMetadata(
    String correlationId,
    String senderId,
    String sessionId,  // nullable
    MessageType messageType,
    long timestamp
) {
    public MessageMetadata {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");
        if (messageType == MessageType.REQUEST && senderId == null) {
            throw new IllegalArgumentException("senderId must not be null for REQUEST messages");
        }
    }

    public static MessageMetadata fromMessage(Message message) {
        return new MessageMetadata(
            message.getUserProperty("correlationId"),
            message.getUserProperty("senderId"),
            message.getUserProperty("sessionId"),
            MessageType.valueOf(message.getUserProperty("messageType")),
            Long.parseLong(message.getUserProperty("timestamp"))
        );
    }

    public void applyToMessage(Message message) {
        message.putUserProperty("correlationId", correlationId);
        if (senderId != null) {
            message.putUserProperty("senderId", senderId);
        }
        if (sessionId != null) {
            message.putUserProperty("sessionId", sessionId);
        }
        message.putUserProperty("messageType", messageType.name());
        message.putUserProperty("timestamp", String.valueOf(timestamp));
    }
}

public enum MessageType {
    REQUEST,
    RESPONSE
}
```

---

### CorrelationEntry (Internal)

Internal data structure used by CorrelationManager to track pending requests.

**Attributes**:
- `correlationId`: String - The correlation identifier
- `future`: CompletableFuture<RpcResponse> - Future that will be completed when response arrives
- `timeoutTask`: ScheduledFuture<?> - Scheduled task that will trigger timeout
- `createdAt`: long - Timestamp when entry was created

**Relationships**:
- One CorrelationEntry per pending RpcRequest
- Stored in ConcurrentHashMap<String, CorrelationEntry> keyed by correlationId

**Lifecycle**:
```
Created → Waiting → [Completed (response received) | Timeout | Cancelled]
```

**Java Implementation**:
```java
class CorrelationEntry {
    private final String correlationId;
    private final CompletableFuture<RpcResponse> future;
    private final ScheduledFuture<?> timeoutTask;
    private final long createdAt;

    CorrelationEntry(String correlationId, CompletableFuture<RpcResponse> future,
                     ScheduledFuture<?> timeoutTask) {
        this.correlationId = correlationId;
        this.future = future;
        this.timeoutTask = timeoutTask;
        this.createdAt = System.currentTimeMillis();
    }

    public void complete(RpcResponse response) {
        timeoutTask.cancel(false);
        future.complete(response);
    }

    public void timeout() {
        future.completeExceptionally(
            new RpcTimeoutException("Request timeout after " + (System.currentTimeMillis() - createdAt) + "ms")
        );
    }

    public void cancel() {
        timeoutTask.cancel(false);
        future.cancel(true);
    }

    // Getters
    public String getCorrelationId() { return correlationId; }
    public CompletableFuture<RpcResponse> getFuture() { return future; }
    public long getAge() { return System.currentTimeMillis() - createdAt; }
}
```

---

## Entity Relationship Diagram

```
┌─────────────────┐
│  RpcRequest     │
│  - correlationId│◄───────────┐
│  - senderId     │            │
│  - sessionId    │            │ correlates
│  - payload      │            │
│  - timestamp    │            │
│  - timeoutMillis│            │
└────────┬────────┘            │
         │                     │
         │ belongs to          │
         │                     │
         ▼                     │
┌─────────────────┐      ┌────┴─────────┐
│StreamingSession │      │ RpcResponse  │
│  - sessionId    │      │ - correlationId│
│  - senderId     │      │ - payload    │
│  - requestTopic │      │ - timestamp  │
│  - responseTopic│      │ - success    │
│  - createdAt    │      │ - errorMessage│
│  - lastActivityAt│      └──────────────┘
│  - messageCount │
│  - active       │
└─────────────────┘

┌──────────────────┐
│ MessageMetadata  │
│  - correlationId │
│  - senderId      │
│  - sessionId     │
│  - messageType   │
│  - timestamp     │
└──────────────────┘
         │
         │ embedded in
         ▼
┌──────────────────┐
│  RocketMQ        │
│  Message         │
│  - topic         │
│  - body          │
│  - properties    │
└──────────────────┘
```

## Persistence Strategy

### In-Memory (Primary)
- **CorrelationEntry**: Stored in ConcurrentHashMap during request lifetime
- **StreamingSession**: Stored in ConcurrentHashMap for active sessions only

### Optional Persistence (Future Enhancement)
- **H2 Database**: Can optionally persist StreamingSession metadata for observability
- **RocksDB**: Can optionally persist message payloads for replay/debugging

### No Persistence Needed
- **RpcRequest**: Ephemeral, stored in RocketMQ message queue only
- **RpcResponse**: Ephemeral, stored in RocketMQ message queue only
- **MessageMetadata**: Stored as message properties, no separate persistence

## Data Flow

### Synchronous RPC Call
```
1. Client creates RpcRequest with unique correlationId
2. CorrelationEntry created in ConcurrentHashMap
3. MessageMetadata applied to RocketMQ Message
4. Message sent via Producer.send() to request topic
5. Receiver processes message, creates RpcResponse
6. Response sent to RESPONSE_{senderId} topic
7. Sender's consumer receives response
8. CorrelationEntry retrieved by correlationId
9. CompletableFuture completed with RpcResponse
10. Sync caller unblocks and returns response
```

### Streaming Request
```
1. Client creates StreamingSession with sessionId
2. For each message in stream:
   a. Create RpcRequest with sessionId
   b. Send using MessageQueueSelector(sessionId.hashCode())
3. All messages routed to same queue (ordering guaranteed)
4. Single receiver processes all messages in order
5. Receiver sends response(s) to sender's response topic
6. Sender receives and correlates responses
```

## Configuration Properties

Configuration for data-related settings:

```yaml
rocketmq:
  rpc:
    client:
      # Message settings
      max-payload-size: 4194304  # 4MB (RocketMQ default)
      default-timeout-millis: 30000
      max-timeout-millis: 300000  # 5 minutes

      # Correlation settings
      max-pending-requests: 10000
      correlation-cleanup-interval-seconds: 60

      # Session settings
      session-idle-timeout-seconds: 300  # 5 minutes
      max-concurrent-sessions: 100
```

## Summary

The data model supports all four user stories:
1. **Synchronous RPC**: RpcRequest + RpcResponse with correlation
2. **Asynchronous RPC**: Same as sync but with CompletableFuture
3. **Streaming Request**: Multiple RpcRequests with shared sessionId
4. **Bidirectional Streaming**: StreamingSession tracking multiple request/response pairs

All entities are designed as immutable Java records for thread-safety and clarity. Validation is enforced in record constructors. The model leverages modern Java features (records, enums, CompletableFuture) and follows the project's constitution requirements.
