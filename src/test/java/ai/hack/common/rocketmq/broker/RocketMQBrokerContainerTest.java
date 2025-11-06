package ai.hack.common.rocketmq.broker;

import ai.hack.common.rocketmq.namesrv.RocketMQNameServerContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RocketMQBrokerContainer 测试类
 */
class RocketMQBrokerContainerTest {

    private static final Logger log = LoggerFactory.getLogger(RocketMQBrokerContainerTest.class);

    @TempDir
    private static File tempDir;

    private static RocketMQNameServerContainer nameServerContainer;

    @BeforeAll
    static void setUp() throws Exception {
        // 每个测试前启动 NameServer
        nameServerContainer = RocketMQNameServerContainer.builder()
                .listenPort(19876)
                .rocketmqHome(new File(tempDir, "namesrv").getAbsolutePath())
                .build();
        nameServerContainer.start();

        // 等待 NameServer 启动
        Thread.sleep(2000);
        log.info("Test NameServer started at {}", nameServerContainer.getFullAddress());
    }

    @AfterAll
    static void tearDown() {
        // 每个测试后关闭 NameServer
        if (nameServerContainer != null && nameServerContainer.isRunning()) {
            nameServerContainer.shutdown();
            log.info("Test NameServer stopped");
        }
    }

    @Test
    void testBrokerStartAndShutdown() throws Exception {
        // 创建 Broker 容器
        RocketMQBrokerContainer container = RocketMQBrokerContainer.builder()
                .brokerName("test-broker")
                .clusterName("TestCluster")
                .brokerId(0L)
                .namesrvAddr(nameServerContainer.getFullAddress())
                .listenPort(20911)
                .storePathRootDir(new File(tempDir, "broker").getAbsolutePath())
                .autoCreateTopicEnable(true)
                .build();

        // 验证初始状态
        assertFalse(container.isRunning(), "Broker should not be running initially");
        assertEquals("test-broker", container.getBrokerName());
        assertEquals("TestCluster", container.getClusterName());
        assertEquals(0L, container.getBrokerId());
        assertEquals(20911, container.getListenPort());

        try {
            // 启动 Broker
            container.start();

            // 验证启动状态
            assertTrue(container.isRunning(), "Broker should be running after start");
            log.info("Broker started successfully: {}", container.getBrokerAddress());

            // 等待 Broker 完全启动并注册到 NameServer
            Thread.sleep(3000);

        } finally {
            // 停止 Broker
            container.shutdown();

            // 验证停止状态
            assertFalse(container.isRunning(), "Broker should not be running after shutdown");
            log.info("Broker stopped successfully");
        }
    }

    @Test
    void testDefaultConfiguration() {
        RocketMQBrokerContainer container = RocketMQBrokerContainer.builder()
                .build();

        // 验证默认配置
        assertEquals("broker-a", container.getBrokerName());
        assertEquals("DefaultCluster", container.getClusterName());
        assertEquals(0L, container.getBrokerId());
        assertEquals(10911, container.getListenPort());
        assertEquals("127.0.0.1:9876", container.getNamesrvAddr());
        assertFalse(container.isRunning());
    }

    @Test
    void testMultipleStartCallsIgnored() throws Exception {
        RocketMQBrokerContainer container = RocketMQBrokerContainer.builder()
                .brokerName("test-broker-2")
                .namesrvAddr(nameServerContainer.getFullAddress())
                .listenPort(20912)
                .storePathRootDir(new File(tempDir, "broker2").getAbsolutePath())
                .build();

        try {
            container.start();
            assertTrue(container.isRunning());

            // 第二次启动应该被忽略
            container.start();
            assertTrue(container.isRunning());

            Thread.sleep(1000);
        } finally {
            container.shutdown();
        }
    }

    @Test
    void testMultipleShutdownCallsIgnored() {
        RocketMQBrokerContainer container = RocketMQBrokerContainer.builder()
                .build();

        // 多次关闭未启动的 Broker 应该被忽略
        container.shutdown();
        container.shutdown();

        assertFalse(container.isRunning());
    }

    @Test
    void testCustomConfiguration() {
        RocketMQBrokerContainer container = RocketMQBrokerContainer.builder()
                .brokerName("custom-broker")
                .clusterName("CustomCluster")
                .brokerId(1L)  // Slave
                .namesrvAddr("192.168.1.100:9876")
                .listenPort(10912)
                .storePathRootDir("/custom/path")
                .autoCreateTopicEnable(false)
                .build();

        // 验证自定义配置
        assertEquals("custom-broker", container.getBrokerName());
        assertEquals("CustomCluster", container.getClusterName());
        assertEquals(1L, container.getBrokerId());
        assertEquals(10912, container.getListenPort());
        assertEquals("192.168.1.100:9876", container.getNamesrvAddr());
        assertEquals("127.0.0.1:10912", container.getBrokerAddress());
    }
}
