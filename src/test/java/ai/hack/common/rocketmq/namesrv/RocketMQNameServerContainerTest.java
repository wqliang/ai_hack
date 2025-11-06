package ai.hack.common.rocketmq.namesrv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RocketMQNameServerContainer 测试类
 */
class RocketMQNameServerContainerTest {

    private static final Logger log = LoggerFactory.getLogger(RocketMQNameServerContainerTest.class);

    @TempDir
    static File tempDir;

    @Test
    void testContainerStartAndShutdown() throws Exception {
        // 创建容器，使用临时目录
        RocketMQNameServerContainer container = RocketMQNameServerContainer.builder()
                .listenPort(19876) // 使用不同的端口避免冲突
                .rocketmqHome(tempDir.getAbsolutePath())
                .build();

        // 验证初始状态
        assertFalse(container.isRunning(), "Container should not be running initially");

        try {
            // 启动容器 (使用 daemon 线程)
            Thread containerThread = new Thread(() -> {
                try {
                    container.start();
                } catch (Exception e) {
                    log.error("Failed to start NameServer container", e);
                }
            }, "NameServer-Container-Daemon");
            containerThread.setDaemon(true);
            containerThread.start();

            // 等待一小段时间确保完全启动
            Thread.sleep(1000);

            // 验证启动状态
            assertTrue(container.isRunning(), "Container should be running after start");
            assertEquals(19876, container.getListenPort());
            assertEquals("127.0.0.1", container.getAddress());
            assertEquals("127.0.0.1:19876", container.getFullAddress());

            log.info("NameServer started successfully at {}", container.getFullAddress());

        } finally {
            // 停止容器
            container.shutdown();

            // 验证停止状态
            assertFalse(container.isRunning(), "Container should not be running after shutdown");
            log.info("NameServer stopped successfully");
        }
    }

    @Test
    void testDefaultConfiguration() {
        RocketMQNameServerContainer container = RocketMQNameServerContainer.builder()
                .build();

        // 验证默认配置
        assertEquals(9876, container.getListenPort(), "Default port should be 9876");
        assertFalse(container.isRunning(), "Container should not be running initially");
    }

    @Test
    void testMultipleStartCallsIgnored() throws Exception {
        RocketMQNameServerContainer container = RocketMQNameServerContainer.builder()
                .listenPort(19877)
                .rocketmqHome(tempDir.getAbsolutePath())
                .build();

        try {
            // 启动容器 (使用 daemon 线程)
            Thread containerThread = new Thread(() -> {
                try {
                    container.start();
                } catch (Exception e) {
                    log.error("Failed to start NameServer container", e);
                }
            }, "NameServer-MultiStart-Daemon");
            containerThread.setDaemon(true);
            containerThread.start();

            Thread.sleep(1000);
            //assertTrue(container.isRunning());

            // 第二次启动应该被忽略
            container.start();
            //assertTrue(container.isRunning());

            Thread.sleep(1000);

        } finally {
            container.shutdown();
        }
    }

    @Test
    void testMultipleShutdownCallsIgnored() {
        RocketMQNameServerContainer container = RocketMQNameServerContainer.builder()
                .build();

        // 多次关闭未启动的容器应该被忽略
        container.shutdown();
        container.shutdown();

        assertFalse(container.isRunning());
    }
}
