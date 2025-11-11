# RocketMQ RPC Client 使用指南

## 概述

RocketMQ RPC Client 是一个基于 Apache RocketMQ 5.3.3 的 RPC 通信框架，提供同步/异步请求-响应模式以及单向/双向流式通信能力，特别适用于 AI 场景下的流式交互。

## 核心特性

### 1. 多种通信模式
- **同步 RPC**: 阻塞式请求-响应
- **异步 RPC**: 非阻塞式请求-响应（基于 CompletableFuture）
- **单向流式**: 多条消息顺序发送，最终接收聚合响应
- **双向流式**: 边发送边接收增量响应（适用于 AI 对话）

### 2. 性能优化
- 可配置的生产者/消费者线程池
- 消息压缩支持
- 批量拉取优化
- 连接池管理

### 3. 可靠性保障
- 请求关联跟踪（Correlation ID）
- 超时管理和自动清理
- 会话管理和并发控制
- 异常处理和错误重试

### 4. 可观测性
- 实时性能指标收集
- 定期指标日志输出
- 请求成功率、延迟统计
- 吞吐量监控

## 快速开始

### 1. 依赖配置

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client-java</artifactId>
    <version>5.3.3</version>
</dependency>
```

### 2. Spring Boot 配置

```yaml
rocketmq:
  rpc:
    client:
      # RocketMQ NameServer 地址
      broker-url: localhost:9876

      # 请求主题（所有发送者共享）
      request-topic: RPC_REQUEST

      # 响应主题前缀
      response-topic-prefix: RESPONSE_

      # 默认超时时间（毫秒）
      default-timeout-millis: 30000

      # 最大并发请求数
      max-concurrent-requests: 1000

      # 最大并发会话数
      max-concurrent-sessions: 100

      # 性能优化配置
      send-msg-timeout: 5000
      retry-times-when-send-failed: 2
      retry-times-when-send-async-failed: 2
      max-message-size: 4194304  # 4MB

      # 消费者配置
      consume-thread-min: 4
      consume-thread-max: 16
      pull-batch-size: 32
      consume-message-batch-max-size: 1

      # 性能监控
      metrics-logging-enabled: true
      metrics-logging-interval-seconds: 60
```

### 3. 客户端初始化

```java
@Service
public class MyService {

    @Autowired
    private RpcClient rpcClient;

    @PostConstruct
    public void init() throws RpcException {
        // 客户端已通过 Spring Boot 自动配置启动
        logger.info("RpcClient is ready: {}", rpcClient.isStarted());
    }
}
```

## 使用示例

### 1. 同步 RPC 调用

```java
public void syncExample() {
    try {
        // 准备请求数据
        String request = "Hello, RocketMQ!";
        byte[] payload = request.getBytes(StandardCharsets.UTF_8);

        // 发送同步请求，超时时间 5 秒
        RpcResponse response = rpcClient.sendSync(payload, 5000);

        // 处理响应
        if (response.success()) {
            String result = new String(response.payload(), StandardCharsets.UTF_8);
            logger.info("Received response: {}", result);
        } else {
            logger.error("Request failed: {}", response.errorMessage());
        }

    } catch (RpcTimeoutException e) {
        logger.error("Request timeout", e);
    } catch (RpcException e) {
        logger.error("Request failed", e);
    }
}
```

### 2. 异步 RPC 调用

```java
public void asyncExample() {
    // 准备请求数据
    String request = "Async request";
    byte[] payload = request.getBytes(StandardCharsets.UTF_8);

    // 发送异步请求
    CompletableFuture<RpcResponse> future = rpcClient.sendAsync(payload, 10000);

    // 异步处理响应
    future.thenAccept(response -> {
        if (response.success()) {
            String result = new String(response.payload(), StandardCharsets.UTF_8);
            logger.info("Async response: {}", result);
        }
    }).exceptionally(throwable -> {
        logger.error("Async request failed", throwable);
        return null;
    });

    // 或者等待结果
    try {
        RpcResponse response = future.get(10, TimeUnit.SECONDS);
        logger.info("Got response: {}", new String(response.payload()));
    } catch (Exception e) {
        logger.error("Failed to get response", e);
    }
}
```

### 3. 单向流式通信

```java
public void streamingExample() {
    try {
        // 1. 开始流式会话
        String sessionId = rpcClient.sendStreamingStart();
        logger.info("Started streaming session: {}", sessionId);

        // 2. 发送多条流式消息
        for (int i = 0; i < 10; i++) {
            String message = "Message " + i;
            rpcClient.sendStreamingMessage(
                sessionId,
                message.getBytes(StandardCharsets.UTF_8)
            );
            logger.debug("Sent streaming message {}", i);
        }

        // 3. 结束会话并获取聚合响应
        RpcResponse finalResponse = rpcClient.sendStreamingEnd(sessionId, 30000);

        if (finalResponse.success()) {
            String aggregated = new String(
                finalResponse.payload(),
                StandardCharsets.UTF_8
            );
            logger.info("Streaming completed: {}", aggregated);
        }

    } catch (SessionException e) {
        logger.error("Session error", e);
    } catch (RpcException e) {
        logger.error("Streaming failed", e);
    }
}
```

### 4. 双向流式通信（AI 对话场景）

```java
public void bidirectionalStreamingExample() {
    try {
        // 1. 开始流式会话
        String sessionId = rpcClient.sendStreamingStart();

        // 2. 创建响应处理器
        List<String> responses = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        StreamingResponseHandler handler = new StreamingResponseHandler() {
            @Override
            public void onResponse(RpcResponse response) {
                // 实时接收增量响应
                String chunk = new String(
                    response.payload(),
                    StandardCharsets.UTF_8
                );
                responses.add(chunk);
                System.out.print(chunk); // 实时打印
            }

            @Override
            public void onComplete() {
                completed.set(true);
                System.out.println("\n[Stream completed]");
            }

            @Override
            public void onError(Throwable error) {
                logger.error("Streaming error", error);
            }
        };

        // 3. 发送双向流式消息
        rpcClient.sendBidirectionalMessage(
            sessionId,
            "Tell me a story".getBytes(StandardCharsets.UTF_8),
            handler
        );

        // 可以继续发送更多消息
        rpcClient.sendBidirectionalMessage(
            sessionId,
            "Make it short".getBytes(StandardCharsets.UTF_8),
            handler
        );

        // 4. 结束会话
        RpcResponse finalResponse = rpcClient.sendStreamingEnd(sessionId, 60000);

        logger.info("Total responses received: {}", responses.size());

    } catch (Exception e) {
        logger.error("Bidirectional streaming failed", e);
    }
}
```

### 5. 获取性能指标

```java
public void metricsExample() {
    // 获取性能指标
    RpcClientMetrics metrics = rpcClient.getMetrics();

    // 访问各种指标
    logger.info("Total requests: {}", metrics.getTotalRequests());
    logger.info("Success rate: {:.2f}%", metrics.getSuccessRate());
    logger.info("Average latency: {:.2f}ms", metrics.getAverageLatencyMillis());
    logger.info("Active sessions: {}", metrics.getActiveSessions());
    logger.info("Throughput: {:.2f} req/s", metrics.getRequestsPerSecond());

    // 获取完整摘要
    logger.info("Metrics: {}", metrics.getSummary());
}
```

## 接收端实现

接收端需要实现消息处理逻辑：

```java
@Service
public class RpcReceiver {

    @Autowired
    private DefaultMQPushConsumer consumer;

    @Autowired
    private DefaultMQProducer producer;

    @PostConstruct
    public void init() throws Exception {
        consumer.setNamesrvAddr("localhost:9876");
        consumer.subscribe("RPC_REQUEST", "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                handleRequest(msg);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        producer.start();
    }

    private void handleRequest(MessageExt msg) {
        try {
            // 提取元数据
            String correlationId = msg.getUserProperty("correlationId");
            String senderId = msg.getUserProperty("senderId");
            String sessionId = msg.getUserProperty("sessionId");

            // 处理请求
            String request = new String(msg.getBody(), StandardCharsets.UTF_8);
            String response = processRequest(request);

            // 发送响应
            if (correlationId != null) {
                sendResponse(senderId, correlationId, response);
            }

        } catch (Exception e) {
            logger.error("Failed to handle request", e);
        }
    }

    private void sendResponse(String senderId, String correlationId, String content)
            throws Exception {
        String responseTopic = "RESPONSE_" + senderId;

        Message responseMsg = new Message(
            responseTopic,
            content.getBytes(StandardCharsets.UTF_8)
        );
        responseMsg.putUserProperty("correlationId", correlationId);
        responseMsg.putUserProperty("messageType", "RESPONSE");

        producer.send(responseMsg);
    }
}
```

## 会话路由机制

RPC Client 使用会话 ID 保证同一会话的所有消息路由到同一接收者：

1. **会话创建**: `sendStreamingStart()` 生成唯一会话 ID
2. **消息路由**: 使用 `MessageQueueSelector` 基于会话 ID 哈希选择队列
3. **顺序保证**: 同一队列的消息按 FIFO 顺序处理
4. **会话管理**: 自动跟踪活跃会话，支持并发限制

## 错误处理

### 常见异常类型

- `RpcException`: 通用 RPC 异常
- `RpcTimeoutException`: 请求超时异常
- `SessionException`: 会话相关异常
- `CorrelationException`: 关联 ID 异常

### 最佳实践

```java
public void errorHandlingExample() {
    try {
        RpcResponse response = rpcClient.sendSync(payload, 5000);

        if (!response.success()) {
            // 处理业务层失败
            logger.warn("Business error: {}", response.errorMessage());
            return;
        }

        // 处理成功响应
        processResponse(response);

    } catch (RpcTimeoutException e) {
        // 超时重试逻辑
        logger.error("Request timeout, retrying...", e);
        retryRequest();

    } catch (RpcException e) {
        // RPC 层错误
        logger.error("RPC failed", e);
        handleRpcError(e);

    } catch (Exception e) {
        // 未预期错误
        logger.error("Unexpected error", e);
    }
}
```

## 性能调优建议

### 1. 线程池配置

```yaml
rocketmq:
  rpc:
    client:
      # 低延迟场景
      consume-thread-min: 8
      consume-thread-max: 32

      # 高吞吐场景
      consume-thread-min: 16
      consume-thread-max: 64
      pull-batch-size: 64
```

### 2. 超时配置

- 短请求: 3-5 秒
- 长请求: 10-30 秒
- 流式会话: 30-60 秒

### 3. 并发控制

```yaml
max-concurrent-requests: 1000  # 根据系统容量调整
max-concurrent-sessions: 100   # 根据内存情况调整
```

### 4. 监控启用

```yaml
metrics-logging-enabled: true
metrics-logging-interval-seconds: 60  # 生产环境建议 300（5分钟）
```

## 注意事项

1. **资源管理**: 确保在应用关闭时正确关闭客户端
2. **会话清理**: 长时间未使用的会话会自动清理
3. **消息大小**: 默认最大 4MB，超大消息需特殊处理
4. **网络稳定性**: RocketMQ 需要稳定的网络连接
5. **NameServer 高可用**: 生产环境建议部署多个 NameServer

## 常见问题

### Q1: 如何处理消息顺序？

A: 使用流式会话，同一 sessionId 的消息保证顺序。

### Q2: 如何实现请求重试？

A: RocketMQ Producer 自动重试，也可以在应用层实现重试逻辑。

### Q3: 性能指标如何持久化？

A: 可以集成 Prometheus/Micrometer 导出指标到监控系统。

### Q4: 如何实现负载均衡？

A: RocketMQ 自动在多个消费者之间负载均衡。

## 更多资源

- [Apache RocketMQ 官方文档](https://rocketmq.apache.org/)
- [Spring Boot Integration Guide](https://github.com/apache/rocketmq-spring)
- [RocketMQ Best Practices](https://rocketmq.apache.org/docs/bestPractice/01bestpractice)

---

**版本**: 1.0.0
**最后更新**: 2025-11-11
**作者**: Claude Code
