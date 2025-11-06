package ai.hack.common.rocketmq.client;

import ai.hack.common.rocketmq.broker.RocketMQBrokerContainer;
import ai.hack.common.rocketmq.namesrv.RocketMQNameServerContainer;
import org.apache.rocketmq.common.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Topic 管理示例
 * 演示如何使用 TopicManager 创建、查询和删除 Topic
 */
public class TopicManagerExample {

    private static final Logger log = LoggerFactory.getLogger(TopicManagerExample.class);

    public static void main(String[] args) {
        RocketMQNameServerContainer nameServer = null;
        RocketMQBrokerContainer broker = null;
        TopicManager topicManager = null;

        try {
            // 1. 启动 NameServer
            log.info("Step 1: Starting NameServer...");
            nameServer = RocketMQNameServerContainer.builder()
                    .listenPort(9876)
                    .rocketmqHome("./rocketmq-namesrv-data")
                    .build();
            nameServer.start();
            Thread.sleep(2000);
            log.info("NameServer started at: {}", nameServer.getFullAddress());

            // 2. 启动 Broker
            log.info("Step 2: Starting Broker...");
            broker = RocketMQBrokerContainer.builder()
                    .brokerName("broker-a")
                    .clusterName("DefaultCluster")
                    .brokerId(0L)
                    .namesrvAddr(nameServer.getFullAddress())
                    .listenPort(10911)
                    .storePathRootDir("./rocketmq-broker-data")
                    .autoCreateTopicEnable(false) // 禁用自动创建，手动管理 Topic
                    .build();
            broker.start();
            Thread.sleep(3000);
            log.info("Broker started at: {}", broker.getBrokerAddress());

            // 3. 创建 TopicManager
            log.info("Step 3: Creating TopicManager...");
            topicManager = new TopicManager(nameServer.getFullAddress());
            topicManager.start();

            // 4. 创建 Topic
            log.info("Step 4: Creating topics...");
            String topic1 = "test-topic-1";
            String topic2 = "test-topic-2";
            String topic3 = "test-topic-3";

            // 创建具有默认配置的 Topic（4个读队列，4个写队列）
            topicManager.createTopic(topic1);
            log.info("Created topic: {}", topic1);

            // 创建具有自定义队列数的 Topic
            topicManager.createTopic(topic2, 8, 8);
            log.info("Created topic: {} with 8 read/write queues", topic2);

            // 创建另一个 Topic
            topicManager.createTopic(topic3, 2, 2);
            log.info("Created topic: {} with 2 read/write queues", topic3);

            // 等待 Topic 创建完成
            Thread.sleep(2000);

            // 5. 检查 Topic 是否存在
            log.info("Step 5: Checking if topics exist...");
            log.info("Topic '{}' exists: {}", topic1, topicManager.topicExists(topic1));
            log.info("Topic '{}' exists: {}", topic2, topicManager.topicExists(topic2));
            log.info("Topic '{}' exists: {}", "non-existent-topic",
                    topicManager.topicExists("non-existent-topic"));

            // 6. 获取 Topic 配置
            log.info("Step 6: Getting topic configurations...");
            TopicConfig config1 = topicManager.getTopicConfig(topic1);
            if (config1 != null) {
                log.info("Topic '{}' config - readQueueNums: {}, writeQueueNums: {}",
                        topic1, config1.getReadQueueNums(), config1.getWriteQueueNums());
            }

            TopicConfig config2 = topicManager.getTopicConfig(topic2);
            if (config2 != null) {
                log.info("Topic '{}' config - readQueueNums: {}, writeQueueNums: {}",
                        topic2, config2.getReadQueueNums(), config2.getWriteQueueNums());
            }

            // 7. 列出所有 Topic
            log.info("Step 7: Listing all topics...");
            Set<String> allTopics = topicManager.listTopics();
            log.info("Total topics: {}", allTopics.size());
            allTopics.forEach(topic -> log.info("  - {}", topic));

            // 8. 删除一个 Topic
            log.info("Step 8: Deleting topic...");
            topicManager.deleteTopic(topic3);
            log.info("Deleted topic: {}", topic3);

            Thread.sleep(1000);

            // 验证删除
            log.info("Topic '{}' exists after deletion: {}", topic3, topicManager.topicExists(topic3));

            log.info("=".repeat(60));
            log.info("Topic management example completed successfully!");
            log.info("Press Ctrl+C to stop");
            log.info("=".repeat(60));

            // 添加关闭钩子
            final RocketMQNameServerContainer finalNameServer = nameServer;
            final RocketMQBrokerContainer finalBroker = broker;
            final TopicManager finalTopicManager = topicManager;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Received shutdown signal");

                if (finalTopicManager != null) {
                    finalTopicManager.shutdown();
                }

                if (finalBroker != null) {
                    finalBroker.shutdown();
                }

                if (finalNameServer != null) {
                    finalNameServer.shutdown();
                }

                log.info("Shutdown completed");
            }));

            // 保持运行
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Failed to run topic management example", e);

            // 清理资源
            if (topicManager != null) {
                topicManager.shutdown();
            }
            if (broker != null) {
                broker.shutdown();
            }
            if (nameServer != null) {
                nameServer.shutdown();
            }

            System.exit(1);
        }
    }
}
