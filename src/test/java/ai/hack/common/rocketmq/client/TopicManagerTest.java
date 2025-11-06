package ai.hack.common.rocketmq.client;

import ai.hack.common.rocketmq.broker.RocketMQBrokerContainer;
import ai.hack.common.rocketmq.namesrv.RocketMQNameServerContainer;
import org.apache.rocketmq.common.TopicConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TopicManager 测试类
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TopicManagerTest {

    private static final Logger log = LoggerFactory.getLogger(TopicManagerTest.class);

    @TempDir
    static File tempDir;

    private static RocketMQNameServerContainer nameServer;
    private static RocketMQBrokerContainer broker;
    private static TopicManager topicManager;

    @BeforeAll
    static void setUpAll() throws Exception {
        // 启动 NameServer
        nameServer = RocketMQNameServerContainer.builder()
                .listenPort(19876)
                .rocketmqHome(new File(tempDir, "namesrv").getAbsolutePath())
                .build();
        nameServer.start();
        Thread.sleep(2000);
        log.info("Test NameServer started at {}", nameServer.getFullAddress());

        // 启动 Broker
        broker = RocketMQBrokerContainer.builder()
                .brokerName("test-broker")
                .clusterName("TestCluster")
                .brokerId(0L)
                .namesrvAddr(nameServer.getFullAddress())
                .listenPort(20911)
                .storePathRootDir(new File(tempDir, "broker").getAbsolutePath())
                .autoCreateTopicEnable(false)
                .build();
        broker.start();
        Thread.sleep(13000);
        log.info("Test Broker started at {}", broker.getBrokerAddress());

        // 创建 TopicManager
        topicManager = new TopicManager(nameServer.getFullAddress());
        topicManager.start();
        log.info("TopicManager started");
    }

    @AfterAll
    static void tearDownAll() {
        // 关闭 TopicManager
        if (topicManager != null) {
            topicManager.shutdown();
            log.info("TopicManager stopped");
        }

        // 关闭 Broker
        if (broker != null && broker.isRunning()) {
            broker.shutdown();
            log.info("Test Broker stopped");
        }

        // 关闭 NameServer
        if (nameServer != null && nameServer.isRunning()) {
            nameServer.shutdown();
            log.info("Test NameServer stopped");
        }
    }

    @Test
    @Order(1)
    void testTopicManagerStartAndShutdown() {
        assertTrue(topicManager.isStarted(), "TopicManager should be started");
        assertEquals(nameServer.getFullAddress(), topicManager.getNamesrvAddr());
    }

    @Test
    @Order(2)
    void testCreateTopicWithDefaultConfig() throws Exception {
        String topicName = "test-topic-default";

        // 创建 Topic
        topicManager.createTopic(topicName);

        // 等待创建完成
        Thread.sleep(1000);

        // 验证 Topic 存在
        assertTrue(topicManager.topicExists(topicName), "Topic should exist after creation");

        // 验证配置
        TopicConfig config = topicManager.getTopicConfig(topicName);
        assertNotNull(config, "Topic config should not be null");
        assertEquals(topicName, config.getTopicName());
        assertEquals(4, config.getReadQueueNums(), "Default read queue nums should be 4");
        assertEquals(4, config.getWriteQueueNums(), "Default write queue nums should be 4");

        log.info("Topic '{}' created with default config", topicName);
    }

    @Test
    @Order(3)
    void testCreateTopicWithCustomConfig() throws Exception {
        String topicName = "test-topic-custom";
        int readQueues = 8;
        int writeQueues = 8;

        // 创建 Topic
        topicManager.createTopic(topicName, readQueues, writeQueues);

        // 等待创建完成
        Thread.sleep(1000);

        // 验证 Topic 存在
        assertTrue(topicManager.topicExists(topicName), "Topic should exist after creation");

        // 验证配置
        TopicConfig config = topicManager.getTopicConfig(topicName);
        assertNotNull(config, "Topic config should not be null");
        assertEquals(topicName, config.getTopicName());
        assertEquals(readQueues, config.getReadQueueNums());
        assertEquals(writeQueues, config.getWriteQueueNums());

        log.info("Topic '{}' created with custom config: read={}, write={}",
                topicName, readQueues, writeQueues);
    }

    @Test
    @Order(4)
    void testTopicExists() throws Exception {
        String existingTopic = "test-topic-exists";
        String nonExistingTopic = "non-existing-topic";

        // 创建 Topic
        topicManager.createTopic(existingTopic);
        Thread.sleep(1000);

        // 测试存在的 Topic
        assertTrue(topicManager.topicExists(existingTopic),
                "Existing topic should return true");

        // 测试不存在的 Topic
        assertFalse(topicManager.topicExists(nonExistingTopic),
                "Non-existing topic should return false");

        log.info("Topic existence check passed");
    }

    @Test
    @Order(5)
    void testListTopics() throws Exception {
        // 创建几个 Topic
        topicManager.createTopic("list-test-1");
        topicManager.createTopic("list-test-2");
        topicManager.createTopic("list-test-3");
        Thread.sleep(1000);

        // 列出所有 Topic
        Set<String> topics = topicManager.listTopics();

        assertNotNull(topics, "Topics list should not be null");
        assertFalse(topics.isEmpty(), "Topics list should not be empty");

        // 验证创建的 Topic 在列表中
        assertTrue(topics.contains("list-test-1"), "Topic list should contain list-test-1");
        assertTrue(topics.contains("list-test-2"), "Topic list should contain list-test-2");
        assertTrue(topics.contains("list-test-3"), "Topic list should contain list-test-3");

        log.info("Found {} topics", topics.size());
        topics.forEach(topic -> log.info("  - {}", topic));
    }

    @Test
    @Order(6)
    void testDeleteTopic() throws Exception {
        String topicName = "test-topic-delete";

        // 创建 Topic
        topicManager.createTopic(topicName);
        Thread.sleep(1000);

        // 验证创建成功
        assertTrue(topicManager.topicExists(topicName), "Topic should exist before deletion");

        // 删除 Topic
        topicManager.deleteTopic(topicName);
        Thread.sleep(1000);

        // 验证删除成功
        assertFalse(topicManager.topicExists(topicName), "Topic should not exist after deletion");

        log.info("Topic '{}' deleted successfully", topicName);
    }

    @Test
    @Order(7)
    void testGetTopicConfig() throws Exception {
        String topicName = "test-topic-config";
        int readQueues = 6;
        int writeQueues = 6;

        // 创建 Topic
        topicManager.createTopic(topicName, readQueues, writeQueues);
        Thread.sleep(1000);

        // 获取配置
        TopicConfig config = topicManager.getTopicConfig(topicName);

        assertNotNull(config, "Topic config should not be null");
        assertEquals(topicName, config.getTopicName());
        assertEquals(readQueues, config.getReadQueueNums());
        assertEquals(writeQueues, config.getWriteQueueNums());
        assertTrue(config.getPerm() > 0, "Topic should have permissions");

        log.info("Topic config retrieved: name={}, read={}, write={}, perm={}",
                config.getTopicName(), config.getReadQueueNums(),
                config.getWriteQueueNums(), config.getPerm());
    }

    @Test
    @Order(8)
    void testCreateMultipleTopics() throws Exception {
        int topicCount = 5;

        for (int i = 0; i < topicCount; i++) {
            String topicName = "multi-topic-" + i;
            topicManager.createTopic(topicName, 2, 2);
        }

        Thread.sleep(2000);

        // 验证所有 Topic 都创建成功
        for (int i = 0; i < topicCount; i++) {
            String topicName = "multi-topic-" + i;
            assertTrue(topicManager.topicExists(topicName),
                    "Topic " + topicName + " should exist");
        }

        log.info("Successfully created {} topics", topicCount);
    }
}