# Research: RocketMQ RPC Client Wrapper

**Feature**: 004-rocketmq-rpc-client
**Date**: 2025-11-11
**Purpose**: Technical research for implementing RPC-style and streaming communication using RocketMQ 5.3.3

## RocketMQ 5.3.3 Producer API (User Specified)

### Decision
Use RocketMQ 5.3.3 `Producer.send()` interface for sending messages as specified by the user.

### Rationale
- **User Requirement**: Explicitly requested to use RocketMQ 5.3.3 version and Producer.send() interface
- **Synchronous Sending**: Producer.send() provides synchronous message sending with SendResult return value
- **Asynchronous Sending**: Producer also provides sendAsync() for non-blocking operations with callbacks
- **Message Ordering**: Supports message queue selection for session-based routing (same session → same queue)
- **Properties Support**: Allows setting custom message properties for senderId, correlationId, sessionId

### Implementation Approach
```java
// Synchronous send
SendResult result = producer.send(message);

// Asynchronous send
producer.sendAsync(message, new SendCallback() {
    @Override
    public void onSuccess(SendResult sendResult) { /* handle success */ }
    @Override
    public void onException(Throwable e) { /* handle error */ }
});

// Ordered message send (for session routing)
SendResult result = producer.send(message, new MessageQueueSelector() {
    @Override
    public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
        // Use sessionId hash to select same queue
        return mqs.get(arg.hashCode() % mqs.size());
    }
}, sessionId);
```

### Alternatives Considered
- **RocketMQ Spring Starter**: Higher-level abstraction but adds unnecessary complexity and hides low-level control needed for correlation and ordering
- **Custom Async Implementation**: Could build async on top of sync send() but sendAsync() already provides tested async functionality

## Message Correlation Pattern

### Decision
Use UUID-based correlation IDs stored in message user properties with in-memory tracking using ConcurrentHashMap.

### Rationale
- **Uniqueness**: UUID provides globally unique IDs eliminating collision risk
- **Thread-Safety**: ConcurrentHashMap allows safe concurrent access for multiple in-flight requests
- **Performance**: In-memory tracking avoids I/O overhead for correlation lookups
- **Timeout Support**: Map can be combined with ScheduledExecutorService for timeout cleanup
- **Simplicity**: No external storage dependency for correlation state

### Implementation Approach
```java
// Correlation tracking
private final ConcurrentHashMap<String, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();
private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

// Send with correlation
String correlationId = UUID.randomUUID().toString();
CompletableFuture<RpcResponse> future = new CompletableFuture<>();
pendingRequests.put(correlationId, future);

// Setup timeout
timeoutExecutor.schedule(() -> {
    CompletableFuture<RpcResponse> removed = pendingRequests.remove(correlationId);
    if (removed != null) {
        removed.completeExceptionally(new RpcTimeoutException("Request timed out"));
    }
}, timeoutMillis, TimeUnit.MILLISECONDS);

// On response received
CompletableFuture<RpcResponse> future = pendingRequests.remove(correlationId);
if (future != null) {
    future.complete(response);
}
```

### Alternatives Considered
- **RocksDB/H2 Persistence**: Overkill for correlation tracking which is transient by nature; adds I/O overhead
- **Redis**: External dependency not justified for in-memory correlation state
- **Weak References**: Could cause premature cleanup of pending requests under memory pressure

## Response Topic Pattern

### Decision
Dynamic topic creation per sender using naming pattern `RESPONSE_{senderId}` with auto-subscription on client initialization.

### Rationale
- **Isolation**: Each sender gets dedicated response channel avoiding cross-talk
- **Scalability**: Supports unlimited concurrent senders without topic conflicts
- **Message Filtering**: Consumer filter eliminates need to check sender ID at application level
- **Resource Cleanup**: Topics can be deleted when sender disconnects (manual cleanup or TTL-based)

### Implementation Approach
```java
// On client initialization
String senderId = UUID.randomUUID().toString();
String responseTopic = "RESPONSE_" + senderId;

// Create consumer for response topic
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(responseTopic + "_GROUP");
consumer.subscribe(responseTopic, "*");
consumer.registerMessageListener(new MessageListenerConcurrently() {
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String correlationId = msg.getUserProperty("correlationId");
            handleResponse(correlationId, msg.getBody());
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
});
consumer.start();
```

### Alternatives Considered
- **Shared Response Topic with Filtering**: All responses go to single topic, filtered by senderId. Requires MessageSelector but adds overhead and doesn't scale as well
- **Fixed Pool of Response Topics**: Limited scalability, requires sender-to-topic assignment logic

## Session-Based Message Routing (Streaming)

### Decision
Use RocketMQ ordered message feature with MessageQueueSelector to route all messages with same sessionId to same message queue.

### Rationale
- **Ordering Guarantee**: RocketMQ guarantees FIFO order within a single message queue
- **Single Consumer**: Only one consumer processes messages from a queue at a time
- **Native Feature**: Leverages built-in RocketMQ capability without custom logic
- **Hash-Based Selection**: sessionId hash modulo queue count provides deterministic routing

### Implementation Approach
```java
// Session-aware message sending
public void sendStreaming(String sessionId, byte[] payload) {
    Message msg = new Message(requestTopic, payload);
    msg.putUserProperty("sessionId", sessionId);
    msg.putUserProperty("senderId", this.senderId);
    msg.putUserProperty("correlationId", UUID.randomUUID().toString());

    // Use sessionId as selector argument
    producer.send(msg, new MessageQueueSelector() {
        @Override
        public MessageQueue select(List<MessageQueue> mqs, Message msg, Object sessionId) {
            int index = Math.abs(sessionId.hashCode() % mqs.size());
            return mqs.get(index);
        }
    }, sessionId);
}
```

### Alternatives Considered
- **Partition Key**: Similar to MessageQueueSelector but less explicit control
- **Custom Routing Table**: Would require coordination between senders to avoid conflicts
- **Single Queue for All Sessions**: No concurrency, unacceptable performance

## Synchronous vs Asynchronous API Design

### Decision
Provide both sync (blocking with timeout) and async (CompletableFuture-based) interfaces using a unified internal implementation.

### Rationale
- **User Requirement**: Spec explicitly requires both synchronous and asynchronous interfaces
- **Java Best Practice**: CompletableFuture is standard for async operations in modern Java
- **Composability**: CompletableFuture allows chaining, combining, and transforming async operations
- **Timeout Support**: Both sync and async can use same timeout mechanism via ScheduledExecutorService
- **Code Reuse**: Sync implementation can delegate to async with .get(timeout)

### Implementation Approach
```java
public interface RpcClient {
    // Synchronous API - blocks until response or timeout
    RpcResponse sendSync(byte[] payload, long timeoutMillis) throws RpcTimeoutException;

    // Asynchronous API - returns immediately with CompletableFuture
    CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis);

    // Streaming APIs
    void sendStreaming(String sessionId, byte[] payload);
    CompletableFuture<Void> sendStreamingAsync(String sessionId, byte[] payload);
}

// Unified implementation
public RpcResponse sendSync(byte[] payload, long timeoutMillis) throws RpcTimeoutException {
    try {
        return sendAsync(payload, timeoutMillis).get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        throw new RpcTimeoutException("Request timeout", e);
    } catch (InterruptedException | ExecutionException e) {
        throw new RpcException("Request failed", e);
    }
}
```

### Alternatives Considered
- **Callback-Based Async**: Traditional but less composable than CompletableFuture
- **Reactive Streams (Project Reactor)**: Overkill for simple request-response, steeper learning curve
- **Separate Sync/Async Implementations**: Code duplication, harder to maintain

## Lifecycle Management and Resource Cleanup

### Decision
Implement AutoCloseable with explicit lifecycle methods (start/stop) and Spring @PreDestroy hook for automatic cleanup.

### Rationale
- **Resource Safety**: Ensures producers/consumers are properly closed to avoid connection leaks
- **Spring Integration**: @PreDestroy automatically called on application shutdown
- **Explicit Control**: start/stop methods allow manual lifecycle control when needed
- **Pending Request Handling**: Cleanup can complete or cancel pending requests before shutdown

### Implementation Approach
```java
@Service
public class RpcClientImpl implements RpcClient, AutoCloseable {
    private final DefaultMQProducer producer;
    private final DefaultMQPushConsumer consumer;
    private volatile boolean started = false;

    public void start() throws RpcException {
        if (started) return;
        try {
            producer.start();
            consumer.start();
            started = true;
        } catch (MQClientException e) {
            throw new RpcException("Failed to start RPC client", e);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!started) return;

        // Complete pending requests with exception
        pendingRequests.values().forEach(future ->
            future.completeExceptionally(new RpcException("Client shutting down")));
        pendingRequests.clear();

        // Shutdown executor
        timeoutExecutor.shutdown();

        // Close RocketMQ resources
        producer.shutdown();
        consumer.shutdown();
        started = false;
    }
}
```

### Alternatives Considered
- **Finalize Method**: Deprecated in modern Java, unreliable
- **Spring Lifecycle Interfaces**: More verbose than @PreDestroy annotation
- **Manual Cleanup Only**: Error-prone, easy to forget, leads to resource leaks

## Error Handling and Retry Strategy

### Decision
Implement custom exception hierarchy with no automatic retries for RPC calls; retry logic delegated to caller.

### Rationale
- **Explicit Failures**: RPC calls should fail fast and explicitly rather than silently retrying
- **Idempotency Concerns**: Caller knows if operation is idempotent; client shouldn't assume
- **Timeout Clarity**: Retries can confuse timeout semantics (is it per-attempt or total?)
- **Observability**: Clear failure modes make debugging easier than hidden retry loops

### Implementation Approach
```java
// Exception hierarchy
public class RpcException extends RuntimeException { }
public class RpcTimeoutException extends RpcException { }
public class CorrelationException extends RpcException { }
public class SessionException extends RpcException { }

// Error handling in send
public CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis) {
    CompletableFuture<RpcResponse> future = new CompletableFuture<>();
    String correlationId = UUID.randomUUID().toString();

    try {
        Message msg = buildMessage(correlationId, payload);

        producer.sendAsync(msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                pendingRequests.put(correlationId, future);
                scheduleTimeout(correlationId, timeoutMillis);
            }

            @Override
            public void onException(Throwable e) {
                future.completeExceptionally(new RpcException("Send failed", e));
            }
        });
    } catch (Exception e) {
        future.completeExceptionally(new RpcException("Failed to send", e));
    }

    return future;
}
```

### Alternatives Considered
- **Automatic Retry with Backoff**: Adds complexity and hides failures from caller
- **Circuit Breaker Pattern**: Useful for protecting downstream services but overkill for message queue client
- **Checked Exceptions**: Modern Java prefers unchecked for recoverable errors

## Configuration Management

### Decision
Use Spring Boot @ConfigurationProperties with YAML configuration for all client settings.

### Rationale
- **Externalization**: Configuration separate from code as per Spring Boot best practices
- **Type Safety**: @ConfigurationProperties provides compile-time type checking
- **Validation**: Can use JSR-303 validation annotations (@NotNull, @Min, @Max)
- **IDE Support**: Auto-completion in application.yml with proper metadata
- **Profiles**: Different configurations for dev/test/prod environments

### Implementation Approach
```java
@ConfigurationProperties(prefix = "rocketmq.rpc.client")
@Validated
public class RpcClientConfig {
    @NotBlank
    private String brokerUrl = "localhost:9876";

    @NotBlank
    private String requestTopic;

    @Min(100)
    @Max(300000)
    private long defaultTimeoutMillis = 30000;

    @Min(1)
    @Max(100)
    private int maxConcurrentRequests = 1000;

    private String responseTopicPrefix = "RESPONSE_";

    // getters/setters
}

// application.yml
rocketmq:
  rpc:
    client:
      broker-url: ${ROCKETMQ_BROKER_URL:localhost:9876}
      request-topic: RPC_REQUEST
      default-timeout-millis: 30000
      max-concurrent-requests: 1000
      response-topic-prefix: RESPONSE_
```

### Alternatives Considered
- **@Value Annotations**: Verbose, no grouping, poor IDE support
- **Hardcoded Defaults**: Inflexible, requires recompilation for changes
- **Properties Files**: Less human-readable than YAML

## Summary of Technical Decisions

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| **Messaging Library** | RocketMQ 5.3.3 Producer.send() | User specified version and interface |
| **Correlation Tracking** | UUID + ConcurrentHashMap | Thread-safe, performant, no external dependencies |
| **Response Topics** | Per-sender dynamic topics | Isolation, scalability, clean separation |
| **Session Routing** | MessageQueueSelector with hash | Native RocketMQ ordering guarantee |
| **Async API** | CompletableFuture | Modern Java standard, composable |
| **Sync API** | Blocking on CompletableFuture | Code reuse, consistent timeout handling |
| **Lifecycle** | AutoCloseable + @PreDestroy | Spring integration, explicit control |
| **Error Handling** | Custom exception hierarchy | Clear failure modes, no hidden retries |
| **Configuration** | @ConfigurationProperties + YAML | Type-safe, validated, externalizable |

## Performance Considerations

1. **Memory Management**: Bounded ConcurrentHashMap size with eviction policy for abandoned requests
2. **Thread Pools**: Separate executor for timeout tasks to avoid blocking message processing threads
3. **Consumer Concurrency**: Configure consumer thread pool size based on expected throughput
4. **Connection Pooling**: RocketMQ Producer/Consumer internally pool connections
5. **Batch Processing**: For streaming scenarios, consider batching small messages to reduce overhead

## Testing Strategy

1. **Unit Tests**: Mock RocketMQ Producer/Consumer, test correlation logic, timeout handling, session routing
2. **Integration Tests**: Use TestContainers with embedded RocketMQ broker for end-to-end tests
3. **Concurrency Tests**: Simulate 1000+ concurrent requests to verify thread-safety
4. **Timeout Tests**: Verify proper cleanup of timed-out requests
5. **Session Tests**: Verify message ordering within sessions across multiple messages

## Open Questions Resolved

1. **Q: How to handle topic cleanup for disconnected senders?**
   A: Manual cleanup via admin API or rely on RocketMQ's topic TTL configuration

2. **Q: Should we persist correlation state for crash recovery?**
   A: No - RPC calls are inherently ephemeral; crashed client should retry

3. **Q: How to handle broker unavailability?**
   A: Fail fast with clear exception; reconnection handled by RocketMQ client library

4. **Q: Should we support request cancellation?**
   A: Yes - CompletableFuture.cancel() can remove from pendingRequests map

5. **Q: How to handle response timeouts in streaming scenarios?**
   A: Each response message has own correlation ID with independent timeout
