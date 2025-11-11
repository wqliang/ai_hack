# RPC Client API Contract

**Feature**: 004-rocketmq-rpc-client
**Date**: 2025-11-11
**Type**: Java Interface Contract

## Overview

This contract defines the public API for the RocketMQ RPC Client Wrapper. The API provides both synchronous (blocking) and asynchronous (non-blocking) methods for RPC-style communication and streaming interactions.

## Main Interface: RpcClient

### Purpose
Primary interface for sending RPC requests and managing client lifecycle.

### Methods

#### sendSync
Sends a request synchronously and blocks until response is received or timeout occurs.

**Signature**:
```java
RpcResponse sendSync(byte[] payload, long timeoutMillis) throws RpcTimeoutException, RpcException
```

**Parameters**:
- `payload`: byte[] - The request data (non-null, can be empty)
- `timeoutMillis`: long - Timeout in milliseconds (1 to 300000)

**Returns**:
- `RpcResponse` - The response from the receiver

**Throws**:
- `RpcTimeoutException` - If no response received within timeout period
- `RpcException` - If send operation fails or client is not started

**Preconditions**:
- Client must be in started state
- payload must not be null
- timeoutMillis must be > 0 and <= 300000

**Postconditions**:
- Request is sent to request topic with unique correlation ID
- Method blocks until response received or timeout
- If successful, RpcResponse with success=true is returned
- If receiver returns error, RpcResponse with success=false is returned
- If timeout, RpcTimeoutException is thrown

**Example**:
```java
RpcClient client = ...; // injected or created
client.start();

try {
    byte[] request = "Hello".getBytes();
    RpcResponse response = client.sendSync(request, 5000); // 5 second timeout

    if (response.success()) {
        String result = new String(response.payload());
        System.out.println("Received: " + result);
    } else {
        System.err.println("Error: " + response.errorMessage());
    }
} catch (RpcTimeoutException e) {
    System.err.println("Request timed out");
} finally {
    client.close();
}
```

---

#### sendAsync
Sends a request asynchronously and returns immediately with a CompletableFuture.

**Signature**:
```java
CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis)
```

**Parameters**:
- `payload`: byte[] - The request data (non-null, can be empty)
- `timeoutMillis`: long - Timeout in milliseconds (1 to 300000)

**Returns**:
- `CompletableFuture<RpcResponse>` - Future that will be completed with response or exception

**Exceptions (via CompletableFuture)**:
- `RpcTimeoutException` - If no response received within timeout period
- `RpcException` - If send operation fails

**Preconditions**:
- Client must be in started state
- payload must not be null
- timeoutMillis must be > 0 and <= 300000

**Postconditions**:
- Method returns immediately without blocking
- Request is sent to request topic asynchronously
- CompletableFuture is completed when response arrives or timeout occurs
- Calling thread is not blocked

**Example**:
```java
RpcClient client = ...;
client.start();

byte[] request = "Hello".getBytes();
CompletableFuture<RpcResponse> future = client.sendAsync(request, 5000);

// Option 1: Register callback
future.thenAccept(response -> {
    if (response.success()) {
        System.out.println("Async response: " + new String(response.payload()));
    }
}).exceptionally(ex -> {
    System.err.println("Async error: " + ex.getMessage());
    return null;
});

// Option 2: Wait for result later
try {
    RpcResponse response = future.get(); // blocks here
} catch (Exception e) {
    e.printStackTrace();
}
```

---

#### sendStreamingStart
Starts a streaming session and returns the session ID for subsequent messages.

**Signature**:
```java
String sendStreamingStart() throws RpcException
```

**Parameters**: None

**Returns**:
- `String` - Unique session ID (UUID format)

**Throws**:
- `RpcException` - If session creation fails or client is not started

**Preconditions**:
- Client must be in started state
- Client must have capacity for new session (< max concurrent sessions)

**Postconditions**:
- New StreamingSession is created with unique sessionId
- Session is tracked in internal session map
- Session is in active state

**Example**:
```java
RpcClient client = ...;
client.start();

String sessionId = client.sendStreamingStart();
System.out.println("Started session: " + sessionId);
```

---

#### sendStreamingMessage
Sends a message as part of an existing streaming session.

**Signature**:
```java
void sendStreamingMessage(String sessionId, byte[] payload) throws SessionException, RpcException
```

**Parameters**:
- `sessionId`: String - The session ID returned from sendStreamingStart (non-null)
- `payload`: byte[] - The message data (non-null, can be empty)

**Throws**:
- `SessionException` - If sessionId is unknown or session is not active
- `RpcException` - If send operation fails

**Preconditions**:
- Session with sessionId must exist and be active
- payload must not be null

**Postconditions**:
- Message is sent to request topic with sessionId in metadata
- Message is routed to same queue as previous messages in this session
- Session's lastActivityAt and messageCount are updated

**Example**:
```java
String sessionId = client.sendStreamingStart();

// Send multiple messages in stream
client.sendStreamingMessage(sessionId, "Part 1".getBytes());
client.sendStreamingMessage(sessionId, "Part 2".getBytes());
client.sendStreamingMessage(sessionId, "Part 3".getBytes());

// All messages guaranteed to be processed by same receiver in order
```

---

#### sendStreamingEnd
Ends a streaming session and waits for final response.

**Signature**:
```java
RpcResponse sendStreamingEnd(String sessionId, long timeoutMillis)
    throws SessionException, RpcTimeoutException, RpcException
```

**Parameters**:
- `sessionId`: String - The session ID to end (non-null)
- `timeoutMillis`: long - Timeout for receiving final response

**Returns**:
- `RpcResponse` - The final response from receiver after processing all messages

**Throws**:
- `SessionException` - If sessionId is unknown
- `RpcTimeoutException` - If no response within timeout
- `RpcException` - If operation fails

**Preconditions**:
- Session with sessionId must exist

**Postconditions**:
- Session is marked as inactive
- Method blocks until final response received or timeout
- Session is removed from active session map

**Example**:
```java
String sessionId = client.sendStreamingStart();
client.sendStreamingMessage(sessionId, "Message 1".getBytes());
client.sendStreamingMessage(sessionId, "Message 2".getBytes());

RpcResponse finalResponse = client.sendStreamingEnd(sessionId, 10000);
System.out.println("Stream completed: " + new String(finalResponse.payload()));
```

---

#### start
Initializes the client and starts RocketMQ producer/consumer.

**Signature**:
```java
void start() throws RpcException
```

**Parameters**: None

**Throws**:
- `RpcException` - If initialization fails (broker unreachable, configuration error, etc.)

**Preconditions**:
- Client must not already be started
- Configuration must be valid (broker URL, topics, etc.)

**Postconditions**:
- RocketMQ producer is started and ready to send
- RocketMQ consumer is started and subscribed to response topic
- Client is in started state
- Client can accept send requests

**Example**:
```java
RpcClient client = ...;
client.start(); // Must call before sending any requests
```

---

#### close
Shuts down the client and releases all resources. Implements AutoCloseable.

**Signature**:
```java
void close()
```

**Parameters**: None

**Throws**: None (exceptions are logged but not thrown)

**Preconditions**:
- None (idempotent, safe to call multiple times)

**Postconditions**:
- All pending requests are cancelled with exception
- All active sessions are terminated
- RocketMQ producer and consumer are shut down
- Thread pools are shut down
- Client is in stopped state
- Subsequent send attempts will throw RpcException

**Example**:
```java
// Try-with-resources (recommended)
try (RpcClient client = new RpcClientImpl(...)) {
    client.start();
    // use client
} // close() automatically called

// Manual close
RpcClient client = ...;
try {
    client.start();
    // use client
} finally {
    client.close(); // ensures cleanup even if errors occur
}
```

---

#### isStarted
Checks if the client is currently started and ready to use.

**Signature**:
```java
boolean isStarted()
```

**Parameters**: None

**Returns**:
- `boolean` - true if client is started, false otherwise

**Preconditions**: None

**Postconditions**: None (read-only query method)

**Example**:
```java
RpcClient client = ...;
if (!client.isStarted()) {
    client.start();
}
```

---

## Receiver Interface: RpcReceiver

### Purpose
Interface for implementing custom request processing logic. Receivers handle incoming RPC requests and produce responses.

### Methods

#### processRequest
Processes a single RPC request and produces a response.

**Signature**:
```java
byte[] processRequest(byte[] requestPayload) throws Exception
```

**Parameters**:
- `requestPayload`: byte[] - The request data sent by sender

**Returns**:
- `byte[]` - The response data to send back to sender

**Throws**:
- `Exception` - Any processing exception (will be wrapped in error response)

**Preconditions**:
- requestPayload is valid for this receiver's protocol

**Postconditions**:
- If successful, returns response payload
- If exception thrown, error response is sent with exception message

**Example**:
```java
public class EchoReceiver implements RpcReceiver {
    @Override
    public byte[] processRequest(byte[] requestPayload) throws Exception {
        // Simple echo - return request as response
        return requestPayload;
    }
}

public class UpperCaseReceiver implements RpcReceiver {
    @Override
    public byte[] processRequest(byte[] requestPayload) throws Exception {
        String input = new String(requestPayload, StandardCharsets.UTF_8);
        String output = input.toUpperCase();
        return output.getBytes(StandardCharsets.UTF_8);
    }
}
```

---

#### processStreamingRequest
Processes multiple messages from a streaming session and produces response(s).

**Signature**:
```java
byte[] processStreamingRequest(String sessionId, List<byte[]> messages) throws Exception
```

**Parameters**:
- `sessionId`: String - The session identifier for this stream
- `messages`: List<byte[]> - All messages received in this session (in order)

**Returns**:
- `byte[]` - The aggregated response to send back to sender

**Throws**:
- `Exception` - Any processing exception

**Preconditions**:
- messages list is non-empty and ordered by receipt time
- All messages belong to the same session

**Postconditions**:
- Returns single aggregated response for entire session

**Example**:
```java
public class ConcatenateReceiver implements RpcReceiver {
    @Override
    public byte[] processStreamingRequest(String sessionId, List<byte[]> messages) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte[] msg : messages) {
            result.append(new String(msg, StandardCharsets.UTF_8));
            result.append(" ");
        }
        return result.toString().trim().getBytes(StandardCharsets.UTF_8);
    }
}
```

---

## Exception Hierarchy

```
java.lang.RuntimeException
    └── RpcException (base exception for all RPC errors)
        ├── RpcTimeoutException (request timeout)
        ├── CorrelationException (correlation ID mismatch/missing)
        └── SessionException (session not found/invalid)
```

### RpcException
**Purpose**: Base exception for all RPC client errors
**When thrown**: Send failures, configuration errors, client not started
**Recovery**: Check client state, verify configuration, retry if appropriate

### RpcTimeoutException
**Purpose**: Request did not receive response within timeout period
**When thrown**: sendSync() or sendStreamingEnd() timeout expires
**Recovery**: Retry with longer timeout, check receiver availability

### CorrelationException
**Purpose**: Response correlation ID does not match any pending request
**When thrown**: Received response with unknown/expired correlation ID
**Recovery**: Should not occur in normal operation, indicates client/receiver mismatch

### SessionException
**Purpose**: Session operation on unknown or inactive session
**When thrown**: sendStreamingMessage() or sendStreamingEnd() with invalid sessionId
**Recovery**: Call sendStreamingStart() to create new session

---

## Thread Safety

**Thread-Safe Operations** (can be called concurrently from multiple threads):
- sendAsync()
- sendStreamingMessage() (different sessions)
- close()
- isStarted()

**Not Thread-Safe** (caller must synchronize):
- sendSync() - blocking call, caller should synchronize if needed
- Multiple sendStreamingMessage() calls for same session from different threads (undefined order)

**Lifecycle Methods** (should be called from single thread):
- start() - call once at initialization
- close() - call once at shutdown

---

## Performance Characteristics

### sendSync()
- **Latency**: Network RTT + processing time + serialization overhead (~50-100ms typical)
- **Throughput**: Limited by blocking nature, use sendAsync() for high throughput
- **Resource Usage**: One pending correlation entry until response/timeout

### sendAsync()
- **Latency**: Returns immediately (~1ms), actual response time varies
- **Throughput**: Can handle 1000+ concurrent requests
- **Resource Usage**: One pending correlation entry per request

### sendStreaming*()
- **Latency**: Depends on session duration and message count
- **Throughput**: Limited by message ordering constraint (serial processing per session)
- **Resource Usage**: One session entry + multiple correlation entries

---

## Configuration

Configuration via application.yml (see data-model.md for details):

```yaml
rocketmq:
  rpc:
    client:
      broker-url: localhost:9876
      request-topic: RPC_REQUEST
      default-timeout-millis: 30000
      max-concurrent-requests: 1000
      max-concurrent-sessions: 100
```

---

## Usage Examples

### Simple Synchronous RPC
```java
@Service
public class MyService {
    private final RpcClient rpcClient;

    public MyService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String callRemoteService(String input) {
        try {
            byte[] request = input.getBytes();
            RpcResponse response = rpcClient.sendSync(request, 5000);

            if (response.success()) {
                return new String(response.payload());
            } else {
                throw new RuntimeException("RPC failed: " + response.errorMessage());
            }
        } catch (RpcTimeoutException e) {
            throw new RuntimeException("RPC timeout", e);
        }
    }
}
```

### Asynchronous with Multiple Requests
```java
public void processMultipleRequests(List<String> inputs) {
    List<CompletableFuture<RpcResponse>> futures = inputs.stream()
        .map(input -> rpcClient.sendAsync(input.getBytes(), 10000))
        .collect(Collectors.toList());

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenRun(() -> {
            futures.forEach(future -> {
                try {
                    RpcResponse resp = future.get();
                    System.out.println("Response: " + new String(resp.payload()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
}
```

### Streaming Request
```java
public String processStreamingRequest(List<String> messages) {
    String sessionId = rpcClient.sendStreamingStart();

    for (String msg : messages) {
        rpcClient.sendStreamingMessage(sessionId, msg.getBytes());
    }

    RpcResponse finalResponse = rpcClient.sendStreamingEnd(sessionId, 30000);
    return new String(finalResponse.payload());
}
```

---

## Contract Validation

This contract must be validated by:
1. **Unit Tests**: Mock implementations verifying method contracts
2. **Integration Tests**: Real RocketMQ broker verifying end-to-end behavior
3. **Javadoc**: All methods must have Javadoc matching this contract
4. **Exception Handling**: All declared exceptions must be thrown as specified
