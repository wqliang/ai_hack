# Quick Start Guide: RocketMQ Async Client

**Version**: 1.0.0
**Date**: 2025-11-09
**Library**: RocketMQ Async Client for Java

## Overview

This guide helps you quickly get started with the RocketMQ Async Client library, which provides asynchronous message publishing and consumption capabilities with enterprise-grade features like TLS security, at-least-once delivery guarantees, and FIFO ordering.

## Prerequisites

### Java Environment
- **Java Version**: 11 minimum, 17+ recommended, 21 optimal for virtual threads
- **Memory**: At least 512MB heap space to accommodate the 100MB library footprint
- **Build Tool**: Maven 3.6+ or Gradle 6.0+

### RocketMQ Server
- **Version**: RocketMQ 5.3.0+ recommended
- **Access**: NameServer address and necessary credentials
- **Network**: Network connectivity to RocketMQ brokers

## Installation

### Maven Dependency

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- RocketMQ Async Client -->
    <dependency>
        <groupId>ai.hack</groupId>
        <artifactId>rocketmq-async-client</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Spring Boot Integration (optional) -->
    <dependency>
        <groupId>ai.hack</groupId>
        <artifactId>rocketmq-async-client-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### Gradle Dependency

Add to your `build.gradle`:

```groovy
dependencies {
    // Core library
    implementation 'ai.hack:rocketmq-async-client:1.0.0'

    // Spring Boot starter (optional)
    implementation 'ai.hack:rocketmq-async-client-spring-boot-starter:1.0.0'
}
```

## Basic Usage

### 1. Configuration

Create a client configuration:

```java
import ai.hack.rocketmq.config.ClientConfiguration;
import java.time.Duration;

// Basic configuration
ClientConfiguration config = ClientConfiguration.builder()
    .namesrvAddr("localhost:9876")
    .producerGroup("my-app-producer")
    .consumerGroup("my-app-consumer")
    .build();

// Advanced configuration with security
ClientConfiguration secureConfig = ClientConfiguration.builder()
    .namesrvAddr("broker1:9876,broker2:9876")
    .producerGroup("secure-producer")
    .consumerGroup("secure-consumer")
    .enableTls(true)
    .authentication("your-access-key", "your-secret-key")
    .maxMessageSize(4 * 1024 * 1024) // 4MB
    .sendTimeout(Duration.ofSeconds(5))
    .retryTimes(3)
    .persistence("/var/app/rocketmq-data", Duration.ofSeconds(10))
    .build();
```

### 2. Initialize Client

```java
import ai.hack.rocketmq.RocketMQAsyncClient;
import ai.hack.rocketmq.DefaultRocketMQAsyncClient;

// Create and initialize client
RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
try {
    client.initialize(config);
    System.out.println("RocketMQ client initialized successfully");
} catch (RocketMQException e) {
    System.err.println("Failed to initialize client: " + e.getMessage());
}
```

### 3. Send Messages

#### Asynchronous Message Publishing

```java
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.result.SendResult;
import java.util.concurrent.CompletableFuture;

// Create message
Message message = Message.builder()
    .topic("user.events")
    .payload("User john.doe registered".getBytes())
    .header("event-type", "user.registered")
    .header("user-id", "12345")
    .priority(MessagePriority.NORMAL)
    .build();

// Send asynchronously
CompletableFuture<SendResult> future = client.sendMessageAsync(message);

// Handle result
future.thenAccept(result -> {
    if (result.isSuccess()) {
        System.out.println("Message sent successfully: " + result.getMessageId());
        System.out.println("Topic: " + result.getTopic());
        System.out.println("Processing time: " + result.getProcessingTime().toMillis() + "ms");
    } else {
        System.err.println("Send failed: " + result.getErrorMessage());
    }
}).exceptionally(throwable -> {
    System.err.println("Send error: " + throwable.getMessage());
    return null;
});
```

#### Synchronous Message Publishing

```java
try {
    SendResult result = client.sendMessageSync(message, Duration.ofSeconds(5));
    if (result.isSuccess()) {
        System.out.println("Sync send completed: " + result.getMessageId());
    }
} catch (TimeoutException e) {
    System.err.println("Send timed out: " + e.getMessage());
} catch (RocketMQException e) {
    System.err.println("Send failed: " + e.getMessage());
}
```

#### Batch Message Publishing

```java
import java.util.Arrays;
import java.util.List;

List<Message> messages = Arrays.asList(
    Message.builder().topic("batch.events").payload("Message 1".getBytes()).build(),
    Message.builder().topic("batch.events").payload("Message 2".getBytes()).build(),
    Message.builder().topic("batch.events").payload("Message 3".getBytes()).build()
);

CompletableFuture<BatchSendResult> batchFuture = client.sendBatchAsync(messages);

batchFuture.thenAccept(result -> {
    System.out.println("Batch completed - Total: " + result.getTotalCount());
    System.out.println("Success: " + result.getSuccessCount());
    System.out.println("Failed: " + result.getFailureCount());
});
```

### 4. Consume Messages

#### Simple Message Consumption

```java
import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.callback.MessageProcessingResult;

try {
    client.subscribe("user.events", message -> {
        try {
            System.out.println("Received message from topic: " + message.getTopic());
            System.out.println("Message ID: " + message.getMessageId());
            System.out.println("Content: " + new String(message.getPayload()));

            // Process message payload
            String payload = new String(message.getPayload());
            // ... your business logic here ...

            // Return success
            return MessageProcessingResult.success();

        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            return MessageProcessingResult.failure("Processing error", e);
        }
    });

    System.out.println("Subscribed to user.events topic");

} catch (RocketMQException e) {
    System.err.println("Subscription failed: " + e.getMessage());
}
```

#### Message Processing with Response

```java
client.subscribe("user.queries", message -> {
    String userId = new String(message.getPayload());

    try {
        // Process user query (e.g., database lookup)
        String userData = lookupUserData(userId);

        // Create response message
        Message response = Message.builder()
            .topic(message.getCallbackTopic())  // Auto-resolve from callback
            .payload(userData.getBytes())
            .header("response-to", message.getMessageId())
            .build();

        return MessageProcessingResult.success(response);

    } catch (Exception e) {
        return MessageProcessingResult.failure("Query processing failed", e);
    }
});
```

### 5. Request-Response Pattern

```java
// Send request and wait for response
Message request = Message.builder()
    .topic("user.queries")
    .payload("Get user:123".getBytes())
    .callbackTopic("user.responses")  // Where to send response
    .header("query-type", "get-user")
    .build();

CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(5));

responseFuture.thenAccept(response -> {
    System.out.println("Response received: " + new String(response.getPayload()));
}).orTimeout(5, TimeUnit.SECONDS)
.exceptionally(throwable -> {
    if (throwable instanceof java.util.concurrent.TimeoutException) {
        System.err.println("Request timed out");
    } else {
        System.err.println("Request failed: " + throwable.getMessage());
    }
    return null;
});
```

## Spring Boot Integration

### Application Configuration

Create `application.yml`:

```yaml
rocketmq:
  client:
    namesrv-addr: localhost:9876
    producer-group: ${spring.application.name}-producer
    consumer-group: ${spring.application.name}-consumer
    max-message-size: 4194304  # 4MB
    send-timeout: 3s
    request-timeout: 5s
    retry-times: 3
    tls-enabled: false
    persistence-enabled: true
    persistence-path: ./rocketmq-data
    compression-enabled: true
```

### Configuration Properties Class

```java
import ai.hack.rocketmq.config.ClientConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rocketmq.client")
public class SpringRocketMQConfiguration {

    private String namesrvAddr;
    private String producerGroup;
    private String consumerGroup;
    private int maxMessageSize = 2 * 1024 * 1024;
    private Duration sendTimeout = Duration.ofSeconds(3);
    private boolean tlsEnabled = false;
    // ... other properties with getters/setters

    @Bean
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
            .namesrvAddr(namesrvAddr)
            .producerGroup(producerGroup)
            .consumerGroup(consumerGroup)
            .maxMessageSize(maxMessageSize)
            .sendTimeout(sendTimeout)
            .enableTls(tlsEnabled)
            .build();
    }

    @Bean
    public RocketMQAsyncClient rocketMQClient(ClientConfiguration config) {
        DefaultRocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);
            return client;
        } catch (RocketMQException e) {
            throw new RuntimeException("Failed to initialize RocketMQ client", e);
        }
    }
}
```

### Service Component

```java
import org.springframework.stereotype.Service;

@Service
public class UserEventService {

    private final RocketMQAsyncClient rocketMQClient;

    public UserEventService(RocketMQAsyncClient rocketMQClient) {
        this.rocketMQClient = rocketMQClient;

        // Initialize subscriptions
        initializeSubscriptions();
    }

    public void publishUserRegisteredEvent(User user) {
        Message message = Message.builder()
            .topic("user.events")
            .payload(convertUserToJson(user).getBytes())
            .header("event-type", "user.registered")
            .header("user-id", user.getId())
            .build();

        rocketMQClient.sendMessageAsync(message)
            .thenAccept(result -> log.info("User event published: {}", result.getMessageId()))
            .exceptionally(ex -> {
                log.error("Failed to publish user event", ex);
                return null;
            });
    }

    private void initializeSubscriptions() {
        try {
            rocketMQClient.subscribe("user.commands", this::handleUserCommands);
        } catch (RocketMQException e) {
            log.error("Failed to initialize subscriptions", e);
        }
    }

    private MessageProcessingResult handleUserCommands(Message message) {
        String command = new String(message.getPayload());
        // Process command...
        return MessageProcessingResult.success();
    }
}
```

## Advanced Features

### Message Priority

```java
Message highPriorityMessage = Message.builder()
    .topic("urgent.notifications")
    .payload("Critical alert".getBytes())
    .priority(MessagePriority.HIGH)
    .build();

Message criticalMessage = Message.builder()
    .topic("system.alerts")
    .payload("CRITICAL: System failure".getBytes())
    .priority(MessagePriority.CRITICAL)
    .build();
```

### Message Tags

```java
Message message = Message.builder()
    .topic("orders")
    .payload("Order #12345 created".getBytes())
    .tag("order.created")
    .tag("priority.high")
    .build();
```

### Custom Headers

```java
Message message = Message.builder()
    .topic("audit.events")
    .payload("User action".getBytes())
    .header("user-id", "12345")
    .header("action", "login")
    .header("ip-address", "192.168.1.100")
    .header("timestamp", Instant.now().toString())
    .build();
```

### Performance Monitoring

```java
// Get client status
ClientStatus status = client.getClientStatus();
System.out.println("Client state: " + status.getState());
System.out.println("Active connections: " + status.getMetrics().getActiveConnections());
System.out.println("Messages sent: " + status.getMetrics().getMessagesSent());
System.out.println("Average latency: " + status.getMetrics().getAverageLatency() + "ms");
System.out.println("Throughput: " + status.getMetrics().getThroughput() + " msgs/sec");
```

### Optimized Configuration for High Throughput

```java
ClientConfiguration highThroughputConfig = ClientConfiguration.builder()
    .namesrvAddr("broker1:9876,broker2:9876,broker3:9876")
    .producerGroup("high-throughput-producer")
    .maxConnections(128)
    .maxMessageSize(8 * 1024 * 1024) // 8MB
    .sendTimeout(Duration.ofSeconds(10))
    .retryTimes(5)
    .compressionEnabled(true)
    .persistence("/opt/fast-ssd/rocketmq-data", Duration.ofSeconds(2))
    .maxConsumeThreads(256)
    .healthCheckInterval(Duration.ofSeconds(10))
    .build();
```

## Error Handling

### Exception Types

```java
try {
    client.sendMessageAsync(message).get();
} catch (ConnectionException e) {
    log.error("Cannot connect to RocketMQ broker: {}", e.getMessage());
    // Implement retry logic or fallback
} catch (TimeoutException e) {
    log.error("Message send timed out: {}", e.getMessage());
    // Retry with different timeout or queue for later
} catch (MessageSizeException e) {
    log.error("Message too large: {}", e.getMessage());
    // Split message or compress payload
} catch (RocketMQException e) {
    log.error("General RocketMQ error: {}", e.getMessage());
    // Handle based on error code
}
```

### Retry Strategy

```java
public CompletableFuture<SendResult> sendWithRetry(Message message, int maxRetries) {
    CompletableFuture<SendResult> future = client.sendMessageAsync(message);

    return future.handle((result, throwable) -> {
        if (throwable != null && maxRetries > 0) {
            log.warn("Send failed, retrying... attempts left: {}", maxRetries);
            return sendWithRetry(message, maxRetries - 1);
        } else if (throwable != null) {
            return CompletableFuture.failedFuture(throwable);
        } else {
            return CompletableFuture.completedFuture(result);
        }
    }).thenCompose(cf -> cf);
}
```

## Best Practices

### Resource Management

```java
// Use try-with-resources for automatic cleanup
try (RocketMQAsyncClient client = new DefaultRocketMQAsyncClient()) {
    client.initialize(config);

    // Use client...

} catch (Exception e) {
    log.error("Client error", e);
}

// Or explicitly shutdown
client.shutdown(Duration.ofSeconds(30));
```

### Connection Pool Optimization

```java
// For high-volume applications
ClientConfiguration optimizedConfig = ClientConfiguration.builder()
    .namesrvAddr(namesrvAddr)
    .maxConnections(64)  // Start with 64, adjust based on load
    .healthCheckInterval(Duration.ofSeconds(15))
    .build();
```

### Message Size Management

```java
// Validate message size before sending
public Message createValidMessage(String topic, byte[] payload) {
    int maxSize = 2 * 1024 * 1024; // 2MB default
    if (payload.length > maxSize) {
        throw new IllegalArgumentException(
            String.format("Message size %d exceeds limit %d", payload.length, maxSize));
    }

    return Message.builder()
        .topic(topic)
        .payload(payload)
        .build();
}
```

### Monitoring and Health Checks

```java
@Scheduled(fixedRate = 30000) // Every 30 seconds
public void healthCheck() {
    ClientStatus status = client.getClientStatus();

    if (status.getState() != ClientState.CONNECTED) {
        log.warn("RocketMQ client not connected, state: {}", status.getState());
        // Alert monitoring system
    }

    if (status.getMetrics().getErrorRate() > 0.05) { // 5% error rate
        log.warn("High error rate detected: {}%", status.getMetrics().getErrorRate() * 100);
        // Alert monitoring system
    }
}
```

## Troubleshooting

### Common Issues

1. **Connection Failures**
   - Verify NameServer address is correct
   - Check network connectivity
   - Validate credentials if TLS enabled

2. **Timeout Issues**
   - Increase timeout configuration
   - Check broker performance
   - Verify network latency

3. **Memory Issues**
   - Monitor memory usage in metrics
   - Adjust persistence flush intervals
   - Tune JVM memory parameters

4. **Performance Issues**
   - Check connection pool size
   - Verify message compression settings
   - Monitor broker cluster health

### Logging

Add logging configuration to debug issues:

```yaml
# application.yml
logging:
  level:
    ai.hack.rocketmq: DEBUG
    org.apache.rocketmq: INFO
```

## Next Steps

1. **Explore Advanced Features**: TLS configuration, custom serialization, batch processing
2. **Integration Testing**: Use TestContainers for integration testing
3. **Production Deployment**: Monitoring, health checks, performance tuning
4. **Custom Extensions**: Build custom message processors, plugins

---

**Documentation Status**: Final
**Support**: For issues and questions, refer to the project documentation or submit an issue.