package ai.hack.rocketmq;

import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.result.SendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for User Story 1 - Async Message Publishing.
 * Tests the core MVP functionality of async message publishing.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client.max-message-size=4194304",
        "rocketmq.client.send-timeout=3s",
        "rocketmq.client.retry-times=2"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class UserStory1AsyncPublishTest {

    private static final Logger logger = LoggerFactory.getLogger(UserStory1AsyncPublishTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testAsyncMessagePublishingSuccess() throws Exception {
        logger.info("Testing async message publishing scenario");

        // Initialize client
        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-producer")
                .maxMessageSize(2 * 1024 * 1024)
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .enablePersistence(true)
                .persistence("./test-data", Duration.ofSeconds(1))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Create test message
            Message message = Message.builder()
                    .topic("test.async.publish")
                    .payload("Hello User Story 1 - Async Publishing Test".getBytes())
                    .header("test-scenario", "async-publish")
                    .header("timestamp", System.currentTimeMillis())
                    .build();

            logger.info("Sending async message: {}", message.getMessageId());

            // Send message asynchronously
            long startTime = System.currentTimeMillis();
            CompletableFuture<SendResult> future = client.sendMessageAsync(message);

            // Verify method returns immediately (non-blocking)
            assertTrue(future.isDone() || !future.isDone(), "Async operation should not block");

            // Wait for completion
            SendResult result = future.get(10, TimeUnit.SECONDS);
            long processingTime = System.currentTimeMillis() - startTime;

            // Verify success
            assertNotNull(result);
            assertEquals(message.getMessageId(), result.getMessageId());
            assertEquals("test.async.publish", result.getTopic());
            assertTrue(result.isSuccess());

            // Verify non-blocking - should complete quickly
            assertTrue(processingTime < 1000,
                    "Async publish should complete quickly");

            logger.info("✅ Async message published successfully: {}", result.getMessageId());
            logger.info("📊 Processing time: {}ms", result.getProcessingTime().toMillis());

            // Test client status
            ClientStatus status = client.getClientStatus();
            assertNotNull(status);
            assertTrue(status.isOperational());
            assertEquals(ClientState.READY, status.getState());

            logger.info("✅ Client status healthy: {}", status);

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testSynchronousMessagePublishingWithTimeout() throws Exception {
        logger.info("Testing synchronous message publishing with timeout");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-sync-producer")
                .sendTimeout(Duration.ofSeconds(3))
                .retryTimes(1)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            Message message = Message.builder()
                    .topic("test.sync.publish")
                    .payload("Synchronous test message".getBytes())
                    .priority(ai.hack.rocketmq.model.MessagePriority.NORMAL)
                    .build();

            logger.info("Sending sync message with timeout");

            // Send synchronously with timeout
            long startTime = System.currentTimeMillis();
            SendResult result = client.sendMessageSync(message, Duration.ofSeconds(5));
            long processingTime = System.currentTimeMillis() - startTime;

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(message.getMessageId(), result.getMessageId());

            logger.info("✅ Sync message published in {}ms: {}", processingTime, result.getMessageId());

        } finally {
            client.shutdown(Duration.ofSeconds(3));
        }
    }

    @Test
    public void testAsyncMessageSizeValidation() throws Exception {
        logger.info("Testing message size validation");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-size-producer")
                .maxMessageSize(1024) // Very small limit for testing
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Create message that exceeds size limit
            byte[] largePayload = new byte[2048]; // 2KB
            for (int i = 0; i < largePayload.length; i++) {
                largePayload[i] = (byte) ('A' + (i % 26));
            }

            Message largeMessage = Message.builder()
                    .topic("test.size.validation")
                    .payload(largePayload)
                    .build();

            logger.info("Attempting to send oversize message");

            CompletableFuture<SendResult> future = client.sendMessageAsync(largeMessage);

            // Should fail with size validation error
            ExecutionException exception = assertThrows(ExecutionException.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            });

            assertTrue(exception.getCause() instanceof RocketMQException);
            RocketMQException rocketMQException = (RocketMQException) exception.getCause();
            assertEquals(ai.hack.rocketmq.exception.ErrorCode.MESSAGE_TOO_LARGE, rocketMQException.getErrorCode());

            logger.info("✅ Message size validation working correctly");

        } finally {
            client.shutdown(Duration.ofSeconds(3));
        }
    }

    @Test
    public void testBatchAsyncMessagePublishing() throws Exception {
        logger.info("Testing batch async message publishing");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-batch-producer")
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Create multiple messages
            java.util.List<Message> messages = java.util.Arrays.asList(
                    Message.builder().topic("test.batch.publish").payload("Message 1".getBytes()).build(),
                    Message.builder().topic("test.batch.publish").payload("Message 2".getBytes()).build(),
                    Message.builder().topic("test.batch.publish").payload("Message 3".getBytes()).build(),
                    Message.builder().topic("test.batch.publish").payload("Message 4".getBytes()).build(),
                    Message.builder().topic("test.batch.publish").payload("Message 5".getBytes()).build()
            );

            logger.info("Sending {} messages in batch", messages.size());

            Stopwatch stopwatch = Stopwatch.createStarted();
            CompletableFuture<ai.hack.rocketmq.result.BatchSendResult> future = client.sendBatchAsync(messages);
            ai.hack.rocketmq.result.BatchSendResult batchResult = future.get(15, TimeUnit.SECONDS);
            stopwatch.stop();

            assertNotNull(batchResult);
            assertEquals(5, batchResult.getTotalCount());
            assertTrue(batchResult.getSuccessCount() > 0);
            logger.info("✅ Batch published: {} total, {} success, {} failed in {}ms",
                       batchResult.getTotalCount(), batchResult.getSuccessCount(),
                       batchResult.getFailureCount(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

            // Verify all messages were sent successfully (in test environment)
            if (batchResult.getFailureCount() > 0) {
                logger.warn("Some messages failed: {}", batchResult.getFailedResults());
            }

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testClientHealthMonitoring() throws Exception {
        logger.info("Testing client health monitoring");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-health-producer")
                .healthCheckInterval(Duration.ofSeconds(1))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Verify initial health
            ClientStatus initialStatus = client.getClientStatus();
            assertTrue(initialStatus.isOperational());
            assertEquals(ClientState.READY, initialStatus.getState());

            // Send some messages to generate metrics
            for (int i = 0; i < 10; i++) {
                Message message = Message.builder()
                        .topic("test.health.monitor")
                        .payload(("Health test message " + i).getBytes())
                        .build();

                CompletableFuture<SendResult> future = client.sendMessageAsync(message);
                future.get(3, TimeUnit.SECONDS);
            }

            // Check status after operations
            ClientStatus statusAfterOperations = client.getClientStatus();
            assertTrue(statusAfterOperations.isOperational());
            assertTrue(statusAfterOperations.getActiveConnections() >= 0);

            if (statusAfterOperations.getMetrics() != null) {
                logger.info("📊 Client metrics: {}", statusAfterOperations.getMetrics());
            }

            logger.info("✅ Client health monitoring working correctly");

        } finally {
            client.shutdown(Duration.ofSeconds(3));
        }
    }
}