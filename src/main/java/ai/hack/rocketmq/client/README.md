# RocketMQ Topic Manager

## 简介

`TopicManager` 是一个用于管理 RocketMQ Topic 的工具类，提供创建、删除、查询 Topic 等功能。

## 功能特性

- 创建 Topic（支持自定义队列数量）
- 删除 Topic
- 检查 Topic 是否存在
- 获取 Topic 配置信息
- 列出所有 Topic
- 支持集群级别的操作

## 快速开始

### 1. 基本使用

```java
// 创建 TopicManager
TopicManager manager = new TopicManager("127.0.0.1:9876");
manager.start();

try {
    // 创建 Topic（使用默认配置：4个读队列，4个写队列）
    manager.createTopic("my-topic");

    // 检查 Topic 是否存在
    boolean exists = manager.topicExists("my-topic");
    System.out.println("Topic exists: " + exists);

    // 获取 Topic 配置
    TopicConfig config = manager.getTopicConfig("my-topic");
    System.out.println("Read queues: " + config.getReadQueueNums());
    System.out.println("Write queues: " + config.getWriteQueueNums());

} finally {
    manager.shutdown();
}
```

### 2. 创建自定义配置的 Topic

```java
TopicManager manager = new TopicManager("127.0.0.1:9876");
manager.start();

try {
    // 创建具有 8 个读队列和 8 个写队列的 Topic
    manager.createTopic("high-throughput-topic", 8, 8);

    // 创建具有 2 个读队列和 2 个写队列的 Topic
    manager.createTopic("low-throughput-topic", 2, 2);

} finally {
    manager.shutdown();
}
```

### 3. 列出和管理 Topic

```java
TopicManager manager = new TopicManager("127.0.0.1:9876");
manager.start();

try {
    // 列出所有 Topic
    Set<String> topics = manager.listTopics();
    System.out.println("Total topics: " + topics.size());
    topics.forEach(System.out::println);

    // 删除 Topic
    manager.deleteTopic("old-topic");

} finally {
    manager.shutdown();
}
```

### 4. 运行示例程序

```bash
# 编译项目
./gradlew build

# 运行示例（会自动启动 NameServer 和 Broker）
java -cp build/classes/java/main:build/libs/* ai.hack.rocketmq.client.TopicManagerExample
```

## API 说明

### 构造函数

```java
TopicManager(String namesrvAddr)
```
创建 TopicManager 实例
- `namesrvAddr`: NameServer 地址（例如：127.0.0.1:9876）

### 核心方法

#### 启动和关闭

```java
void start() throws MQClientException
```
启动管理客户端，必须在使用其他方法前调用

```java
void shutdown()
```
关闭管理客户端

```java
boolean isStarted()
```
检查管理客户端是否已启动

#### Topic 创建

```java
void createTopic(String topicName) throws Exception
```
创建 Topic（使用默认配置：4个读队列，4个写队列）

```java
void createTopic(String topicName, int readQueueNums, int writeQueueNums) throws Exception
```
创建 Topic（指定队列数量）
- `topicName`: Topic 名称
- `readQueueNums`: 读队列数量
- `writeQueueNums`: 写队列数量

```java
void createTopic(String topicName, int readQueueNums, int writeQueueNums, String clusterName) throws Exception
```
在指定集群创建 Topic
- `clusterName`: 集群名称（null 表示在所有集群创建）

#### Topic 删除

```java
void deleteTopic(String topicName) throws Exception
```
删除 Topic（从所有 Broker 和 NameServer）

```java
void deleteTopic(String topicName, String clusterName) throws Exception
```
从指定集群删除 Topic

#### Topic 查询

```java
boolean topicExists(String topicName)
```
检查 Topic 是否存在

```java
TopicConfig getTopicConfig(String topicName) throws Exception
```
获取 Topic 配置信息

```java
Set<String> listTopics() throws Exception
```
列出所有 Topic

## Topic 配置说明

### 队列数量

- **读队列（readQueueNums）**：消费者可以从多少个队列读取消息
- **写队列（writeQueueNums）**：生产者可以向多少个队列写入消息

队列数量建议：
- 低吞吐量：2-4 个队列
- 中等吞吐量：4-8 个队列
- 高吞吐量：8-16 个队列

### 权限配置

Topic 权限值（perm）：
- 2：只写
- 4：只读
- 6：读写（默认）

## 使用场景

### 场景 1：应用启动时初始化 Topic

```java
@PostConstruct
public void initTopics() throws Exception {
    TopicManager manager = new TopicManager("127.0.0.1:9876");
    manager.start();

    try {
        // 检查并创建必需的 Topic
        if (!manager.topicExists("order-topic")) {
            manager.createTopic("order-topic", 8, 8);
        }

        if (!manager.topicExists("payment-topic")) {
            manager.createTopic("payment-topic", 4, 4);
        }
    } finally {
        manager.shutdown();
    }
}
```

### 场景 2：动态创建 Topic

```java
public void createUserTopic(String userId) throws Exception {
    TopicManager manager = new TopicManager("127.0.0.1:9876");
    manager.start();

    try {
        String topicName = "user-" + userId;
        if (!manager.topicExists(topicName)) {
            manager.createTopic(topicName, 4, 4);
        }
    } finally {
        manager.shutdown();
    }
}
```

### 场景 3：Topic 清理

```java
public void cleanupOldTopics() throws Exception {
    TopicManager manager = new TopicManager("127.0.0.1:9876");
    manager.start();

    try {
        Set<String> topics = manager.listTopics();

        for (String topic : topics) {
            if (topic.startsWith("temp-") && isExpired(topic)) {
                manager.deleteTopic(topic);
            }
        }
    } finally {
        manager.shutdown();
    }
}
```

## 注意事项

1. **启动顺序**：确保 NameServer 和 Broker 已启动
2. **资源管理**：使用完毕后必须调用 `shutdown()`
3. **异常处理**：所有操作都可能抛出异常，需要适当处理
4. **Topic 命名**：使用有意义的名称，避免特殊字符
5. **队列数量**：根据实际负载选择合适的队列数量
6. **删除操作**：删除 Topic 会清除所有消息，谨慎操作

## 测试

运行单元测试：

```bash
./gradlew test --tests "ai.hack.rocketmq.client.TopicManagerTest"
```

## 依赖版本

- Apache RocketMQ: 5.3.3
- Java: 21+
- Spring Boot: 3.3.5

## 故障排查

### 无法连接到 NameServer

**症状**：创建 Topic 时抛出连接异常

**解决方案**：
- 确保 NameServer 已启动
- 检查 NameServer 地址是否正确
- 检查网络连接和防火墙设置

### Topic 创建失败

**症状**：调用 `createTopic()` 抛出异常

**解决方案**：
- 确保 Broker 已启动并注册到 NameServer
- 检查 Broker 配置（特别是 `autoCreateTopicEnable`）
- 查看 Broker 日志了解详细错误

### Topic 不存在

**症状**：`topicExists()` 返回 false

**解决方案**：
- 等待一段时间（Topic 创建需要时间同步）
- 检查 Topic 是否创建成功
- 验证 NameServer 连接是否正常

### 删除 Topic 失败

**症状**：Topic 删除后仍然存在

**解决方案**：
- 等待一段时间（删除操作需要时间同步）
- 重启 Broker（某些情况下需要）
- 检查是否有消费者组仍在使用该 Topic

## 最佳实践

1. **使用 try-finally 模式**：确保 TopicManager 正确关闭
2. **批量操作**：如果需要创建多个 Topic，复用同一个 TopicManager 实例
3. **错误处理**：捕获并记录所有异常
4. **配置验证**：创建后验证 Topic 配置是否符合预期
5. **监控告警**：监控 Topic 创建和删除操作

## 后续扩展

基于此 TopicManager，你可以：
1. 实现 Topic 的自动清理和归档
2. 添加 Topic 配置的批量更新
3. 集成到 Spring Boot 自动配置
4. 实现 Topic 的监控和告警
5. 添加 Topic 的权限管理

## 参考资料

- [Apache RocketMQ 官方文档](https://rocketmq.apache.org/)
- [RocketMQ Topic 最佳实践](https://rocketmq.apache.org/docs/bestPractice/01topic/)
