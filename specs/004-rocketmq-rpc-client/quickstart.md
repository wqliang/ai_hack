# Quickstart: RocketMQ RPC Client Wrapper

**Feature**: 004-rocketmq-rpc-client
**Date**: 2025-11-11
**Purpose**: Quick guide to get started with the RPC Client Wrapper

## Prerequisites

Before using the RPC Client Wrapper, ensure you have:

1. **Java 21** installed (or Java 17 minimum for compatibility mode)
2. **RocketMQ 5.3.3** broker running and accessible
3. **Spring Boot 3.3.5** application set up
4. **Gradle** build tool

## Installation

### 1. Dependencies

The RocketMQ RPC Client is part of the ai-hack project. Dependencies are already configured in `build.gradle`:

```gradle
dependencies {
    // RocketMQ Client 5.3.3
    implementation "org.apache.rocketmq:rocketmq-client:5.3.3"

    // Spring Boot for dependency injection
    implementation "org.springframework.boot:spring-boot-starter-web"

    // Other dependencies...
}
```

### 2. Configuration

Add RPC client configuration to `src/main/resources/application.yml`:

```yaml
rocketmq:
  rpc:
    client:
      # Enable/disable auto-configuration (default: true)
      enabled: true

      # RocketMQ NameServer address
      broker-url: ${ROCKETMQ_NAMESRV_ADDR:localhost:9876}

      # Topic for sending requests (shared by all senders)
      request-topic: RPC_REQUEST

      # Default timeout for RPC calls (milliseconds)
      default-timeout-millis: 30000

      # Maximum concurrent pending requests
      max-concurrent-requests: 1000

      # Maximum concurrent streaming sessions
      max-concurrent-sessions: 100

      # Prefix for response topics (sender ID will be appended)
      response-topic-prefix: RESPONSE_
```

**Configuration Properties:**
- `enabled`: Enable/disable auto-configuration (default: true)
- `broker-url`: RocketMQ NameServer address (supports environment variable)
- `request-topic`: Shared topic for all request messages
- `response-topic-prefix`: Prefix for unique response topics per client
- `default-timeout-millis`: Default timeout (100-300000 ms, default: 30000)
- `max-concurrent-requests`: Max pending requests (1-10000, default: 1000)
- `max-concurrent-sessions`: Max streaming sessions (1-1000, default: 100)

For testing, create `src/test/resources/application-test.yml`:

```yaml
rocketmq:
  rpc:
    client:
      enabled: true
      broker-url: localhost:9876
      request-topic: RPC_REQUEST_TEST
      default-timeout-millis: 5000
      max-concurrent-requests: 100
```

**Spring Boot Auto-Configuration:**

The RPC client is automatically configured and started by Spring Boot when:
1. RocketMQ client classes are on the classpath
2. Configuration properties are set (or use defaults)
3. `rocketmq.rpc.client.enabled` is `true` (default)

No manual bean definition needed! Simply inject `RpcClient` into your services.

## Basic Usage

### 1. Synchronous RPC Call

The simplest way to make an RPC call - send request and wait for response.

```java
import ai.hack.rocketmq.client.RpcClient;
import ai.hack.rocketmq.client.model.RpcResponse;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    private final RpcClient rpcClient;

    // Constructor injection
    public MyService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String callRemoteService() {
        // Client is auto-configured and started by Spring Boot
        try {
            // Prepare request
            String requestData = "Hello, RPC!";
            byte[] requestPayload = requestData.getBytes();

            // Send synchronously with 5 second timeout
            RpcResponse response = rpcClient.sendSync(requestPayload, 5000);

            // Check if successful
            if (response.success()) {
                return new String(response.payload());
            } else {
                System.err.println("Error: " + response.errorMessage());
                return null;
            }

        } catch (RpcTimeoutException e) {
            System.err.println("Request timed out after 5 seconds");
            return null;
        } catch (RpcException e) {
            System.err.println("RPC failed: " + e.getMessage());
            return null;
        }
    }
}
```

**Output**:
```
Received response: <receiver's response>
```

---

### 2. Asynchronous RPC Call

Non-blocking call that returns immediately with a CompletableFuture.

```java
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncService {
    private final RpcClient rpcClient;

    public AsyncService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public void callRemoteServiceAsync() {
        byte[] request = "Async Hello".getBytes();

        // Send asynchronously - returns immediately
        CompletableFuture<RpcResponse> future = rpcClient.sendAsync(request, 10000);

        // Register callbacks
        future
            .thenAccept(response -> {
                if (response.success()) {
                    System.out.println("Async response: " + new String(response.payload()));
                } else {
                    System.err.println("Async error: " + response.errorMessage());
                }
            })
            .exceptionally(ex -> {
                System.err.println("Async exception: " + ex.getMessage());
                return null;
            });

        // Main thread continues without blocking
        System.out.println("Request sent, not waiting for response");
    }

    // Alternative: wait for response later
    public String callAndWait() throws Exception {
        byte[] request = "Wait for me".getBytes();

        CompletableFuture<RpcResponse> future = rpcClient.sendAsync(request, 10000);

        // Do other work here...

        // Block and wait for result when needed
        RpcResponse response = future.get(); // throws if timeout or error
        return new String(response.payload());
    }
}
```

**Output**:
```
Request sent, not waiting for response
(later) Async response: <receiver's response>
```

---

### 3. Streaming Request (Multiple Messages, Single Response)

Send multiple related messages that should be processed together.

```java
@Service
public class StreamingService {
    private final RpcClient rpcClient;

    public StreamingService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String sendStreamingRequest(List<String> messages) {
        try {
            // Start a new streaming session
            String sessionId = rpcClient.sendStreamingStart();
            System.out.println("Started session: " + sessionId);

            // Send multiple messages in the stream
            for (int i = 0; i < messages.size(); i++) {
                String msg = messages.get(i);
                rpcClient.sendStreamingMessage(sessionId, msg.getBytes());
                System.out.println("Sent message " + (i + 1) + ": " + msg);
            }

            // End session and wait for aggregated response
            RpcResponse finalResponse = rpcClient.sendStreamingEnd(sessionId, 30000);

            if (finalResponse.success()) {
                return new String(finalResponse.payload());
            } else {
                return "Error: " + finalResponse.errorMessage();
            }

        } catch (RpcTimeoutException e) {
            return "Streaming timeout";
        } catch (SessionException e) {
            return "Session error: " + e.getMessage();
        }
    }

    // Example usage
    public void example() {
        List<String> chunks = List.of(
            "First chunk of data",
            "Second chunk of data",
            "Third chunk of data"
        );

        String result = sendStreamingRequest(chunks);
        System.out.println("Final result: " + result);
    }
}
```

**Output**:
```
Started session: 12345678-1234-1234-1234-123456789abc
Sent message 1: First chunk of data
Sent message 2: Second chunk of data
Sent message 3: Third chunk of data
Final result: <aggregated response from all chunks>
```

---

### 4. Handling Multiple Concurrent Requests

Process multiple requests in parallel using async API.

```java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class BulkProcessingService {
    private final RpcClient rpcClient;

    public BulkProcessingService(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public List<String> processBatch(List<String> requests) {
        // Send all requests asynchronously
        List<CompletableFuture<RpcResponse>> futures = requests.stream()
            .map(req -> rpcClient.sendAsync(req.getBytes(), 10000))
            .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        // Block until all complete
        allDone.join();

        // Collect results
        return futures.stream()
            .map(future -> {
                try {
                    RpcResponse resp = future.get();
                    return resp.success() ? new String(resp.payload()) : "ERROR";
                } catch (Exception e) {
                    return "EXCEPTION: " + e.getMessage();
                }
            })
            .collect(Collectors.toList());
    }

    // Example: process 100 requests concurrently
    public void example() {
        List<String> requests = IntStream.range(0, 100)
            .mapToObj(i -> "Request " + i)
            .collect(Collectors.toList());

        long start = System.currentTimeMillis();
        List<String> responses = processBatch(requests);
        long duration = System.currentTimeMillis() - start;

        System.out.println("Processed " + responses.size() + " requests in " + duration + "ms");
    }
}
```

**Output**:
```
Processed 100 requests in 523ms
```

---

## Implementing a Receiver

To process incoming requests, implement the `RpcReceiver` interface:

```java
import ai.hack.rocketmq.client.RpcReceiver;
import org.springframework.stereotype.Service;

@Service
public class EchoReceiver implements RpcReceiver {

    @Override
    public byte[] processRequest(byte[] requestPayload) throws Exception {
        // Simple echo - return request as response
        String input = new String(requestPayload);
        System.out.println("Received: " + input);

        // Process and create response
        String output = "Echo: " + input;
        return output.getBytes();
    }

    @Override
    public byte[] processStreamingRequest(String sessionId, List<byte[]> messages) throws Exception {
        // Concatenate all messages in the stream
        StringBuilder result = new StringBuilder();
        for (byte[] msg : messages) {
            result.append(new String(msg)).append(" ");
        }

        String output = "Streamed: " + result.toString().trim();
        return output.getBytes();
    }
}
```

---

## Testing

### Unit Test Example

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RpcClientTest {

    @Mock
    private RpcClient mockClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSyncCall() throws Exception {
        // Arrange
        byte[] request = "test".getBytes();
        RpcResponse expectedResponse = RpcResponse.success("correlation123", "response".getBytes());
        when(mockClient.sendSync(request, 5000)).thenReturn(expectedResponse);

        // Act
        RpcResponse actualResponse = mockClient.sendSync(request, 5000);

        // Assert
        assertEquals(expectedResponse, actualResponse);
        assertTrue(actualResponse.success());
    }
}
```

### Integration Test Example

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RpcClientIntegrationTest {

    @Autowired
    private RpcClient rpcClient;

    @Test
    void testRealRpcCall() throws Exception {
        // Ensure client is started
        if (!rpcClient.isStarted()) {
            rpcClient.start();
        }

        // Send real request to test broker
        byte[] request = "integration test".getBytes();
        RpcResponse response = rpcClient.sendSync(request, 10000);

        // Verify response
        assertNotNull(response);
        assertTrue(response.success());
    }
}
```

---

## Troubleshooting

### Client won't start
**Error**: `RpcException: Failed to start RPC client`

**Solutions**:
- Check RocketMQ broker is running: `telnet localhost 9876`
- Verify broker URL in application.yml is correct
- Check firewall settings allow connection to broker
- Review logs for specific error messages

### Timeout errors
**Error**: `RpcTimeoutException: Request timeout`

**Solutions**:
- Increase timeout value (default 30 seconds may be too short)
- Verify receiver is running and processing messages
- Check broker message queue for backlogs
- Ensure network latency is acceptable

### Correlation errors
**Error**: `CorrelationException: Unknown correlation ID`

**Solutions**:
- Ensure receiver is properly setting correlation ID in responses
- Verify sender and receiver are using same message format
- Check for client restart issues (correlation state is lost)

### Too many pending requests
**Error**: `RpcException: Max concurrent requests exceeded`

**Solutions**:
- Increase `max-concurrent-requests` in configuration
- Use async API to avoid blocking threads
- Add backpressure/throttling in your application
- Monitor and cleanup abandoned requests

---

## Next Steps

1. **Read the contracts**: See `contracts/rpc-client-api.md` for detailed API documentation
2. **Review the data model**: See `data-model.md` for message structures
3. **Implement your receiver**: Create custom business logic for processing requests
4. **Configure for production**: Tune timeouts, concurrency limits, and broker settings
5. **Add monitoring**: Track request rates, latencies, and error rates
6. **Write tests**: Unit tests for business logic, integration tests for end-to-end flows

---

## Additional Resources

- **RocketMQ Documentation**: https://rocketmq.apache.org/docs/quick-start/
- **Spring Boot Configuration**: https://docs.spring.io/spring-boot/docs/current/reference/html/
- **CompletableFuture Guide**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html

---

## Summary

You now know how to:
- ✅ Configure the RPC client
- ✅ Make synchronous RPC calls (blocking)
- ✅ Make asynchronous RPC calls (non-blocking)
- ✅ Send streaming requests (multiple messages)
- ✅ Handle concurrent requests
- ✅ Implement a receiver
- ✅ Write tests
- ✅ Troubleshoot common issues

Happy coding! 🚀
