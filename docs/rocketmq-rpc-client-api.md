# RocketMQ RPC Client API 参考

## 核心接口

### RpcClient

主要的 RPC 客户端接口，提供所有通信方法。

#### 方法列表

##### sendSync
```java
RpcResponse sendSync(byte[] payload, long timeoutMillis)
    throws RpcTimeoutException, RpcException
```

同步发送请求并等待响应。

**参数:**
- `payload` - 请求数据（非空）
- `timeoutMillis` - 超时时间（1-300000 毫秒）

**返回:** `RpcResponse` 响应对象

**异常:**
- `RpcTimeoutException` - 超时异常
- `RpcException` - RPC 异常
- `IllegalArgumentException` - 参数非法

**示例:**
```java
byte[] request = "Hello".getBytes();
RpcResponse response = rpcClient.sendSync(request, 5000);
```

---

##### sendAsync
```java
CompletableFuture<RpcResponse> sendAsync(byte[] payload, long timeoutMillis)
```

异步发送请求，立即返回 Future。

**参数:**
- `payload` - 请求数据（非空）
- `timeoutMillis` - 超时时间（1-300000 毫秒）

**返回:** `CompletableFuture<RpcResponse>` 异步响应

**示例:**
```java
CompletableFuture<RpcResponse> future = rpcClient.sendAsync(payload, 10000);
future.thenAccept(response -> {
    System.out.println("Got response: " + new String(response.payload()));
});
```

---

##### sendStreamingStart
```java
String sendStreamingStart() throws RpcException
```

开始一个新的流式会话。

**返回:** `String` 会话 ID（UUID 格式）

**异常:**
- `RpcException` - 会话创建失败
- `IllegalStateException` - 超过最大并发会话数

**示例:**
```java
String sessionId = rpcClient.sendStreamingStart();
```

---

##### sendStreamingMessage
```java
void sendStreamingMessage(String sessionId, byte[] payload)
    throws SessionException, RpcException
```

在流式会话中发送消息。

**参数:**
- `sessionId` - 会话 ID（非空）
- `payload` - 消息数据（非空）

**异常:**
- `SessionException` - 会话不存在或已关闭
- `RpcException` - 发送失败

**示例:**
```java
rpcClient.sendStreamingMessage(sessionId, "Message 1".getBytes());
```

---

##### sendStreamingEnd
```java
RpcResponse sendStreamingEnd(String sessionId, long timeoutMillis)
    throws SessionException, RpcTimeoutException, RpcException
```

结束流式会话并获取最终响应。

**参数:**
- `sessionId` - 会话 ID（非空）
- `timeoutMillis` - 超时时间（1-300000 毫秒）

**返回:** `RpcResponse` 聚合响应

**异常:**
- `SessionException` - 会话不存在
- `RpcTimeoutException` - 超时异常
- `RpcException` - RPC 异常

**示例:**
```java
RpcResponse response = rpcClient.sendStreamingEnd(sessionId, 30000);
```

---

##### sendBidirectionalMessage
```java
void sendBidirectionalMessage(
    String sessionId,
    byte[] payload,
    StreamingResponseHandler responseHandler
) throws SessionException, RpcException
```

发送双向流式消息并注册响应处理器。

**参数:**
- `sessionId` - 会话 ID（非空）
- `payload` - 消息数据（非空）
- `responseHandler` - 响应处理器（非空）

**异常:**
- `SessionException` - 会话不存在或已关闭
- `RpcException` - 发送失败

**示例:**
```java
StreamingResponseHandler handler = response -> {
    System.out.print(new String(response.payload()));
};
rpcClient.sendBidirectionalMessage(sessionId, payload, handler);
```

---

##### start
```java
void start() throws RpcException
```

启动 RPC 客户端。

**异常:**
- `RpcException` - 启动失败
- `IllegalStateException` - 已经启动

---

##### close
```java
void close()
```

关闭 RPC 客户端并释放资源。此方法是幂等的，可多次调用。

---

##### isStarted
```java
boolean isStarted()
```

检查客户端是否已启动。

**返回:** `boolean` 启动状态

---

##### getMetrics
```java
RpcClientMetrics getMetrics()
```

获取性能指标。

**返回:** `RpcClientMetrics` 指标对象

**异常:**
- `IllegalStateException` - 客户端未启动

---

## 数据模型

### RpcResponse

RPC 响应对象（Java Record）。

**字段:**
- `String correlationId` - 关联 ID
- `boolean success` - 是否成功
- `byte[] payload` - 响应数据
- `String errorMessage` - 错误消息（可选）
- `long timestamp` - 时间戳

**工厂方法:**
```java
// 成功响应
RpcResponse.success(String correlationId, byte[] payload)

// 失败响应
RpcResponse.failure(String correlationId, String errorMessage)
```

---

### MessageMetadata

消息元数据（Java Record）。

**字段:**
- `String correlationId` - 关联 ID（可选）
- `String senderId` - 发送者 ID
- `String sessionId` - 会话 ID（可选）
- `MessageType messageType` - 消息类型
- `long timestamp` - 时间戳

**枚举 MessageType:**
- `REQUEST` - 请求消息
- `RESPONSE` - 响应消息

---

### StreamingSession

流式会话对象（Java Record）。

**字段:**
- `String sessionId` - 会话 ID
- `String senderId` - 发送者 ID
- `String correlationId` - 关联 ID
- `boolean active` - 是否活跃
- `int messageCount` - 消息计数
- `Instant createdAt` - 创建时间
- `Instant lastActivityAt` - 最后活动时间

**工厂方法:**
```java
StreamingSession.create(String sessionId, String senderId, String correlationId)
```

---

### StreamingResponseHandler

流式响应处理器接口（函数式接口）。

**方法:**

```java
void onResponse(RpcResponse response)  // 必须实现
default void onComplete() {}           // 可选实现
default void onError(Throwable error) {}  // 可选实现
```

**示例:**
```java
// Lambda 形式
StreamingResponseHandler handler = response -> {
    processResponse(response);
};

// 完整实现
StreamingResponseHandler handler = new StreamingResponseHandler() {
    @Override
    public void onResponse(RpcResponse response) {
        processResponse(response);
    }

    @Override
    public void onComplete() {
        System.out.println("Stream completed");
    }

    @Override
    public void onError(Throwable error) {
        handleError(error);
    }
};
```

---

## 性能指标

### RpcClientMetrics

性能指标收集器。

#### 请求指标

```java
long getTotalRequests()          // 总请求数
long getSuccessfulRequests()     // 成功请求数
long getFailedRequests()         // 失败请求数
long getTimeoutRequests()        // 超时请求数
double getSuccessRate()          // 成功率（%）
```

#### 延迟指标

```java
double getAverageLatencyMillis()  // 平均延迟（ms）
double getMinLatencyMillis()      // 最小延迟（ms）
double getMaxLatencyMillis()      // 最大延迟（ms）
```

#### 会话指标

```java
long getTotalSessions()          // 总会话数
long getActiveSessions()         // 活跃会话数
long getCompletedSessions()      // 完成会话数
long getStreamingMessages()      // 流式消息数
```

#### 吞吐量指标

```java
long getTotalBytesSent()         // 总发送字节数
long getTotalBytesReceived()     // 总接收字节数
double getRequestsPerSecond()    // 请求速率（req/s）
double getThroughputBytesSentPerSecond()      // 发送吞吐（bytes/s）
double getThroughputBytesReceivedPerSecond()  // 接收吞吐（bytes/s）
```

#### 其他方法

```java
Duration getUptime()             // 运行时长
String getSummary()              // 指标摘要
void reset()                     // 重置指标
```

---

## 配置属性

### 基础配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `rocketmq.rpc.client.broker-url` | String | localhost:9876 | NameServer 地址 |
| `rocketmq.rpc.client.request-topic` | String | RPC_REQUEST | 请求主题 |
| `rocketmq.rpc.client.response-topic-prefix` | String | RESPONSE_ | 响应主题前缀 |
| `rocketmq.rpc.client.default-timeout-millis` | Long | 30000 | 默认超时（ms） |

### 并发控制

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `rocketmq.rpc.client.max-concurrent-requests` | Integer | 1000 | 最大并发请求数 |
| `rocketmq.rpc.client.max-concurrent-sessions` | Integer | 100 | 最大并发会话数 |

### Producer 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `rocketmq.rpc.client.send-msg-timeout` | Integer | 5000 | 发送超时（ms） |
| `rocketmq.rpc.client.retry-times-when-send-failed` | Integer | 2 | 同步重试次数 |
| `rocketmq.rpc.client.retry-times-when-send-async-failed` | Integer | 2 | 异步重试次数 |
| `rocketmq.rpc.client.max-message-size` | Integer | 4194304 | 最大消息大小（4MB） |

### Consumer 配置

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `rocketmq.rpc.client.consume-thread-min` | Integer | 4 | 最小消费线程数 |
| `rocketmq.rpc.client.consume-thread-max` | Integer | 16 | 最大消费线程数 |
| `rocketmq.rpc.client.pull-batch-size` | Integer | 32 | 批量拉取大小 |
| `rocketmq.rpc.client.consume-message-batch-max-size` | Integer | 1 | 批量消费大小 |

### 性能监控

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `rocketmq.rpc.client.metrics-logging-enabled` | Boolean | false | 启用指标日志 |
| `rocketmq.rpc.client.metrics-logging-interval-seconds` | Integer | 60 | 日志间隔（秒） |

---

## 异常层次结构

```
RuntimeException
  └─ RpcException                    // RPC 基础异常
      ├─ RpcTimeoutException        // 超时异常
      ├─ SessionException           // 会话异常
      └─ CorrelationException       // 关联异常
```

### 异常详情

#### RpcException
通用 RPC 异常基类。

**构造方法:**
```java
RpcException(String message)
RpcException(String message, Throwable cause)
```

#### RpcTimeoutException
请求超时异常。

**使用场景:**
- 同步请求超时
- 异步请求超时
- 流式会话结束超时

#### SessionException
会话相关异常。

**常见原因:**
- 会话不存在
- 会话已关闭
- 会话未激活
- 超过最大会话数

#### CorrelationException
关联 ID 相关异常。

**常见原因:**
- 关联 ID 已存在
- 关联 ID 不存在
- 关联超时

---

## 线程安全性

所有公开 API 都是线程安全的，可以在多线程环境中安全使用：

- ✅ `RpcClient` 接口的所有方法
- ✅ `RpcClientMetrics` 的所有方法
- ✅ `CorrelationManager` 内部管理
- ✅ `SessionManager` 内部管理

**注意:** `StreamingResponseHandler` 的回调方法可能在不同线程中执行，需要注意并发安全。

---

## 版本兼容性

| RPC Client 版本 | RocketMQ 版本 | Spring Boot 版本 | Java 版本 |
|----------------|---------------|------------------|-----------|
| 1.0.0          | 5.3.3         | 3.3.5+           | 21+       |

---

**最后更新**: 2025-11-11
**维护者**: Claude Code
