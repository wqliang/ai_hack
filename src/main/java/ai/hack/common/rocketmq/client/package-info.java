package ai.hack.common.rocketmq.client;

/**
 * RocketMQ 客户端工具包
 *
 * 这个包提供了 RocketMQ 客户端相关的工具类，用于管理和操作 RocketMQ。
 *
 * 主要类：
 * - TopicManager: Topic 管理工具类，提供创建、删除、查询 Topic 的功能
 * - TopicManagerExample: 使用示例，演示如何使用 TopicManager
 *
 * 快速开始：
 * <pre>
 * // 1. 创建 TopicManager（连接到 NameServer）
 * TopicManager manager = new TopicManager("127.0.0.1:9876");
 * manager.start();
 *
 * // 2. 创建 Topic
 * manager.createTopic("my-topic", 4, 4);
 *
 * // 3. 检查 Topic 是否存在
 * boolean exists = manager.topicExists("my-topic");
 *
 * // 4. 获取 Topic 配置
 * TopicConfig config = manager.getTopicConfig("my-topic");
 *
 * // 5. 列出所有 Topic
 * Set&lt;String&gt; topics = manager.listTopics();
 *
 * // 6. 删除 Topic
 * manager.deleteTopic("my-topic");
 *
 * // 7. 关闭管理器
 * manager.shutdown();
 * </pre>
 *
 * 版本信息：
 * - RocketMQ: 5.3.3
 * - Java: 21+
 *
 * @see TopicManager
 * @see TopicManagerExample
 */
