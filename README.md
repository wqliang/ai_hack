# AI Hack - Spring AI Demo

A demo project built with Spring AI 1.0.3, demonstrating AI chat capabilities using OpenAI and Ollama, plus a high-performance RocketMQ RPC Client for distributed communication.

## Technologies

- **Java**: JDK 21
- **Framework**: Spring Boot 3.3.5
- **AI Library**: Spring AI 1.0.3
- **Message Queue**: Apache RocketMQ 5.3.3
- **Build Tool**: Gradle 9
- **Version Control**: Git with Git Flow

## Features

### Spring AI Chat
- RESTful API for AI chat interactions
- Support for both OpenAI and Ollama backends
- Customizable system prompts
- Clean architecture with service and controller layers

### RocketMQ RPC Client
- **Synchronous & Asynchronous RPC**: Blocking and non-blocking request-response patterns
- **Streaming Communication**: Single-direction and bidirectional streaming for AI scenarios
- **Session Management**: Automatic session routing with FIFO guarantee
- **Performance Monitoring**: Built-in metrics collection and logging
- **High Performance**: Configurable thread pools, connection pooling, and batch processing
- **Reliability**: Request correlation tracking, timeout management, and auto-retry

## Prerequisites

- JDK 21 or higher
- Gradle 9.x
- OpenAI API key (for OpenAI backend) or Ollama running locally (for Ollama backend)
- Apache RocketMQ 5.3.3+ (for RPC Client features)

## Quick Start

### 1. Start RocketMQ NameServer and Broker

```bash
# Start NameServer
nohup sh bin/mqnamesrv &

# Start Broker
nohup sh bin/mqbroker -n localhost:9876 &
```

### 2. Configure Application

Edit `src/main/resources/application.yml`:

```yaml
# OpenAI Configuration
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

# RocketMQ RPC Client Configuration
rocketmq:
  rpc:
    client:
      broker-url: localhost:9876
      request-topic: RPC_REQUEST
      max-concurrent-requests: 1000
      max-concurrent-sessions: 100
      metrics-logging-enabled: true
```

### 3. Build and Run

```bash
./gradlew build
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

### Spring AI Chat Endpoints

#### Health Check
```bash
curl http://localhost:8080/api/chat/health
```

#### Simple Chat
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, how are you?"}'
```

#### Chat with System Message
```bash
curl -X POST http://localhost:8080/api/chat/with-system \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is the capital of France?",
    "systemMessage": "You are a geography expert."
  }'
```

### RocketMQ RPC Client Usage

#### Synchronous RPC Example
```java
@Autowired
private RpcClient rpcClient;

public void sendSyncRequest() {
    byte[] request = "Hello RocketMQ".getBytes(StandardCharsets.UTF_8);
    RpcResponse response = rpcClient.sendSync(request, 5000);

    if (response.success()) {
        String result = new String(response.payload());
        System.out.println("Response: " + result);
    }
}
```

#### Asynchronous RPC Example
```java
public void sendAsyncRequest() {
    byte[] request = "Async request".getBytes();
    CompletableFuture<RpcResponse> future = rpcClient.sendAsync(request, 10000);

    future.thenAccept(response -> {
        System.out.println("Got async response");
    });
}
```

#### Streaming Communication Example
```java
public void streamingCommunication() {
    // Start streaming session
    String sessionId = rpcClient.sendStreamingStart();

    // Send multiple messages
    for (int i = 0; i < 10; i++) {
        rpcClient.sendStreamingMessage(sessionId, ("Message " + i).getBytes());
    }

    // End session and get aggregated response
    RpcResponse response = rpcClient.sendStreamingEnd(sessionId, 30000);
    System.out.println("Aggregated: " + new String(response.payload()));
}
```

#### Bidirectional Streaming Example (AI Chat)
```java
public void bidirectionalStreaming() {
    String sessionId = rpcClient.sendStreamingStart();

    StreamingResponseHandler handler = new StreamingResponseHandler() {
        @Override
        public void onResponse(RpcResponse response) {
            // Receive incremental responses
            System.out.print(new String(response.payload()));
        }

        @Override
        public void onComplete() {
            System.out.println("\nStream completed");
        }
    };

    rpcClient.sendBidirectionalMessage(
        sessionId,
        "Tell me a story".getBytes(),
        handler
    );

    RpcResponse finalResponse = rpcClient.sendStreamingEnd(sessionId, 60000);
}
```

## Documentation

### RocketMQ RPC Client Documentation
- **[Usage Guide](docs/rocketmq-rpc-client-guide.md)** - Complete usage guide with examples
- **[API Reference](docs/rocketmq-rpc-client-api.md)** - Detailed API documentation

### Configuration Reference

Complete configuration options for RocketMQ RPC Client:

```yaml
rocketmq:
  rpc:
    client:
      # Connection
      broker-url: localhost:9876
      request-topic: RPC_REQUEST
      response-topic-prefix: RESPONSE_

      # Timeouts
      default-timeout-millis: 30000
      send-msg-timeout: 5000

      # Concurrency
      max-concurrent-requests: 1000
      max-concurrent-sessions: 100

      # Producer Performance
      retry-times-when-send-failed: 2
      retry-times-when-send-async-failed: 2
      max-message-size: 4194304

      # Consumer Performance
      consume-thread-min: 4
      consume-thread-max: 16
      pull-batch-size: 32
      consume-message-batch-max-size: 1

      # Monitoring
      metrics-logging-enabled: true
      metrics-logging-interval-seconds: 60
```

## Project Structure

```
ai-hack/
├── src/
│   ├── main/
│   │   ├── java/ai/hack/
│   │   │   ├── AiHackApplication.java
│   │   │   ├── controller/          # REST controllers
│   │   │   │   └── ChatController.java
│   │   │   ├── service/            # Business services
│   │   │   │   └── ChatService.java
│   │   │   ├── dto/                # Data transfer objects
│   │   │   │   ├── ChatRequest.java
│   │   │   │   └── ChatResponse.java
│   │   │   └── rocketmq/
│   │   │       └── client/         # RocketMQ RPC Client
│   │   │           ├── RpcClient.java
│   │   │           ├── RpcClientImpl.java
│   │   │           ├── RpcClientMetrics.java
│   │   │           ├── MessageSender.java
│   │   │           ├── MessageReceiver.java
│   │   │           ├── CorrelationManager.java
│   │   │           ├── SessionManager.java
│   │   │           ├── config/
│   │   │           │   ├── RpcClientConfig.java
│   │   │           │   └── RpcClientAutoConfiguration.java
│   │   │           ├── model/
│   │   │           │   ├── RpcRequest.java
│   │   │           │   ├── RpcResponse.java
│   │   │           │   ├── MessageMetadata.java
│   │   │           │   ├── StreamingSession.java
│   │   │           │   └── StreamingResponseHandler.java
│   │   │           └── exception/
│   │   │               ├── RpcException.java
│   │   │               ├── RpcTimeoutException.java
│   │   │               ├── SessionException.java
│   │   │               └── CorrelationException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── META-INF/
│   │           ├── spring.factories
│   │           └── spring-configuration-metadata.json
│   └── test/
│       ├── java/ai/hack/
│       │   ├── rocketmq/client/
│       │   │   ├── unit/          # Unit tests
│       │   │   └── integration/   # Integration tests
│       │   └── AiHackApplicationTests.java
│       └── resources/
│           └── application-test.yml
├── docs/                          # Documentation
│   ├── rocketmq-rpc-client-guide.md
│   └── rocketmq-rpc-client-api.md
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md                      # Project instructions for Claude Code
└── README.md
```

## Git Flow Branches

- `main` - Production-ready code
- `develop` - Development branch
- `feature/*` - Feature branches
- `release/*` - Release branches
- `hotfix/*` - Hotfix branches

## License

See LICENSE file for details.
