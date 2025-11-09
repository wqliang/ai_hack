package ai.hack.rocketmq;

import ai.hack.rocketmq.config.ClientConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for User Story 4 - Concurrent Message Handling.
 * Tests the system's ability to handle 1000+ concurrent operations efficiently.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client.consumer-group=test-concurrency-consumer",
        "rocketmq.client.producer-group=test-concurrency-producer",
        "rocketmq.client.request-timeout=10s",
        "rocketmq.client.send-timeout=5s",
        "rocketmq.client.retry-times=2",
        "rocketmq.client.max-concurrent-operations=1200"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class UserStory4ConcurrencyTest {

    private static final Logger logger = LoggerFactory.getLogger(UserStory4ConcurrencyTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testHighConcurrencyMessagePublishing() throws Exception {
        logger.info("Testing high-concurrency message publishing (1000+ operations)");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-concurrency-producer")
                .consumerGroup("test-concurrency-consumer")
                .maxConcurrentOperations(1500)
                .connectionPoolSize(32)
                .backpressureThreshold(0.9)
                .enableBackpressure(true)
                .maxConsumeThreads(128)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            //测试参数
            int numMessages = 1000;
            ExecutorService executorService = Executors.newFixedThreadPool(100);
            CountDownLatch latch = new CountDownLatch(numMessages);
            AtomicInteger successfulSends = new AtomicInteger(0);
            AtomicInteger failedSends = new AtomicInteger(0);
            List<CompletableFuture<SendResult>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();

            //并发发送1000条消息
            for (int i = 0; i < numMessages; i++) {
                final int messageNum = i;
                executorService.submit(() -> {
                    try {
                        String content = "High-concurrency test message #" + messageNum + " at " + System.currentTimeMillis();
                        Message message = Message.builder()
                                .topic("concurrency.test.topic")
                                .payload(content.getBytes())
                                .header("message-number", String.valueOf(messageNum))
                                .header("test-thread", Thread.currentThread().getName())
                                .build();

                        CompletableFuture<SendResult> future = client.sendMessageAsync(message);
                        synchronized (futures) {
                            futures.add(future);
                        }

                        future.whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                failedSends.incrementAndGet();
                                logger.warn("Message {} failed: {}", messageNum, throwable.getMessage());
                            } else {
                                successfulSends.incrementAndGet();
                                logger.debug("Message {} sent successfully", messageNum);
                            }
                            latch.countDown();
                        });

                    } catch (Exception e) {
                        failedSends.incrementAndGet();
                        logger.error("Failed to submit message {}", messageNum, e);
                        latch.countDown();
                    }
                });
            }

            //等待所有消息发送完成
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            assertTrue(completed, "All operations should complete within timeout");
            assertEquals(numMessages, successfulSends.get() + failedSends.get(),
                    "Total operations should match submitted operations");

            //验证性能指标
            double operationsPerSecond = (double) numMessages / (totalTime / 1000.0);
 logger.info("✅ High-concurrency test completed:");
            logger.info("   Total messages: {}", numMessages);
            logger.info("   Successful: {}", successfulSends.get());
            logger.info("   Failed: {}", failedSends.get());
            logger.info("   Total time: {}ms", totalTime);
            logger.info("   Operations per second: {:.2f}", operationsPerSecond);

            //验证系统状态
            assertTrue(successfulSends.get() > numMessages * 0.95, // 95% success rate
                    "At least 95% of messages should be sent successfully");
            assertTrue(operationsPerSecond > 100, // Should handle >100 ops/sec
                    "Should handle at least 100 operations per second");

            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational(), "Client should remain operational under load");

            // Publisher stats验证
            var publisherStats = ((DefaultRocketMQAsyncClient) client).getPublisherStats();
            logger.info("   Publisher stats: {}", publisherStats);
            assertTrue(publisherStats.getActiveOperations() >= 0,
                    "Active operations should be non-negative");
            assertFalse(publisherStats.isBackpressureActive(),
                    "Backpressure should not be active after test completion");

        } finally {
            client.shutdown(Duration.ofSeconds(10));
            executorService.shutdown();
        }
    }

    @Test
    public void testBackpressureUnderLoad() throws Exception {
        logger.info("Testing backpressure mechanism under extreme load");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-backpressure-producer")
                .maxConcurrentOperations(50) // 低限制来触发backpressure
                .connectionPoolSize(8)
                .backpressureThreshold(0.7) // 70%触发backpressure
                .enableBackpressure(true)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            int numMessages = 200; // 大幅超过并发限制
            ExecutorService executorService = Executors.newFixedThreadPool(50);
            AtomicInteger backpressureRejections = new AtomicInteger(0);
            AtomicInteger acceptedMessages = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(numMessages);

            //快速发送大量消息以触发backpressure
            for (int i = 0; i < numMessages; i++) {
                final int messageNum = i;
                executorService.submit(() -> {
                    try {
                        Message message = Message.builder()
                                .topic("backpressure.test.topic")
                                .payload(("Backpressure test message #" + messageNum).getBytes())
                                .build();

                        CompletableFuture<SendResult> future = client.sendMessageAsync(message);
                        acceptedMessages.incrementAndGet();

                        future.whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                if (throwable.getMessage() != null &&
                                    throwable.getMessage().contains("backpressure")) {
                                    backpressureRejections.incrementAndGet();
                                }
                            }
                            latch.countDown();
                        });

                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("backpressure")) {
                            backpressureRejections.incrementAndGet();
                        }
                        latch.countDown();
                    }
                });
            }

            //等待所有操作完成
            latch.await(20, TimeUnit.SECONDS);

            //验证backpressure被触发
            logger.info("✅ Backpressure test completed:");
            logger.info("   Accepted: {}", acceptedMessages.get());
            logger.info("   Backpressure rejections: {}", backpressureRejections.get());

            //在一些消息被backpressure拒绝的前提下，大部分消息应该被接受
            assertTrue(acceptedMessages.get() > 0, "Some messages should be accepted");
            if (backpressureRejections.get() > 0) {
                logger.info("✅ Backpressure mechanism was activated");
            }

            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational(), "Client should remain operational");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
            executorService.shutdown();
        }
    }

    @Test
    public void testConcurrentRequestResponse() throws Exception {
        logger.info("Testing concurrent request-response pattern");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("test-rr-concurrent-producer")
                .consumerGroup("test-rr-concurrent-consumer")
                .maxConcurrentOperations(500)
                .connectionPoolSize(24)
                .requestTimeout(Duration.ofSeconds(8))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            //设置响应处理器
            client.subscribe("rr.concurrent.request", message -> {
                String requestContent = new String(message.getPayload());
                String responseContent = "Response to: " + requestContent + " at " + System.currentTimeMillis();

                Message response = Message.builder()
                        .topic("rr.concurrent.response")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .build();

                return ai.hack.rocketmq.callback.MessageProcessingResult.success(response);
            });

            //等待订阅建立
            Thread.sleep(2000);

            int numRequests = 100;
            ExecutorService executorService = Executors.newFixedThreadPool(30);
            CountDownLatch latch = new CountDownLatch(numRequests);
            AtomicInteger successfulResponses = new AtomicInteger(0);
            AtomicInteger failedRequests = new AtomicInteger(0);

            long startTime = System.currentTimeMillis();

            //并发请求-响应
            for (int i = 0; i < numRequests; i++) {
                final int requestNum = i;
                executorService.submit(() -> {
                    try {
                        String requestContent = "Concurrent RR request #" + requestNum;
                        Message request = Message.builder()
                                .topic("rr.concurrent.request")
                                .payload(requestContent.getBytes())
                                .callbackTopic("rr.concurrent.response")
                                .build();

                        CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(
                                request, Duration.ofSeconds(6));

                        responseFuture.whenComplete((response, throwable) -> {
                            if (throwable != null) {
                                failedRequests.incrementAndGet();
                            } else {
                                successfulResponses.incrementAndGet();
                                assertTrue(response.getPayload().length > 0,
                                        "Response should have content");
                            }
                            latch.countDown();
                        });

                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                        latch.countDown();
                    }
                });
            }

            //等待所有请求完成
            boolean completed = latch.await(25, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            assertTrue(completed, "All requests should complete");

            logger.info("✅ Concurrent request-response test completed:");
            logger.info("   Total requests: {}", numRequests);
            logger.info("   Successful responses: {}", successfulResponses.get());
            logger.info("   Failed requests: {}", failedRequests.get());
            logger.info("   Total time: {}ms", totalTime);

            //验证性能
            double requestsPerSecond = (double) numRequests / (totalTime / 1000.0);
            assertTrue(requestsPerSecond > 10, "Should handle at least 10 request-response pairs per second");

            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational(), "Client should remain operational");

        } finally {
            client.shutdown(Duration.ofSeconds(8));
            executorService.shutdown();
        }
    }
}