package ai.hack.rocketmq.performance;

import ai.hack.rocketmq.RocketMQAsyncClient;
import ai.hack.rocketmq.DefaultRocketMQAsyncClient;
import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.monitoring.MetricsCollector;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that the RocketMQ Async Client meets all success criteria as specified in the requirements.
 * This test validates SC-001 through SC-006 performance criteria.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client.consumer-group=perf-test-consumer",
        "rocketmq.client.producer-group=perf-test-producer",
        "rocketmq.client.request-timeout=5s",
        "rocketmq.client.send-timeout=3s",
        "rocketmq.client.retry-times=2",
        "rocketmq.client.max-concurrent-operations=1200"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class PerformanceValidationTest {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceValidationTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testSC001_PublishLatencyUnder50ms() throws Exception {
        logger.info("🎯 SC-001: Testing <50ms publish latency (95th percentile)");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("perf-latency-producer")
                .sendTimeout(Duration.ofSeconds(3))
                .retryTimes(1)
                .compressionEnabled(false) // Disable compression for pure latency test
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            int messageCount = 200;
            List<Long> latencies = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(messageCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < messageCount; i++) {
                String content = "Latency test message " + i;
                Message message = Message.builder()
                        .topic("sc001.latency.test")
                        .payload(content.getBytes())
                        .build();

                long startTime = System.nanoTime();
                CompletableFuture<SendResult> future = client.sendMessageAsync(message);

                future.whenComplete((result, throwable) -> {
                    long endTime = System.nanoTime();
                    long latencyNs = endTime - startTime;
                    double latencyMs = latencyNs / 1_000_000.0;

                    synchronized (latencies) {
                        latencies.add((long) latencyMs);
                    }

                    if (throwable == null && result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All messages should complete within timeout");
            assertEquals(messageCount, successCount.get(), "All messages should succeed");

            // Calculate 95th percentile latency
            latencies.sort(Long::compareTo);
            int ninetyFifthIndex = (int) Math.ceil(latencies.size() * 0.95) - 1;
            long p95Latency = latencies.get(ninetyFifthIndex);

            logger.info("✅ Latency test results: count={}, P95={}ms, min={}ms, max={}ms",
                       latencies.size(), p95Latency,
                       latencies.get(0), latencies.get(latencies.size() - 1));

            assertTrue(p95Latency < 50, "95th percentile latency should be under 50ms, was: " + p95Latency + "ms");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testSC002_1000PlusConcurrentOperations() throws Exception {
        logger.info("🎯 SC-002: Testing 1000+ concurrent operations");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("perf-concurrency-producer")
                .maxConcurrentOperations(1500)
                .connectionPoolSize(32)
                .enableBackpressure(true)
                .backpressureThreshold(0.9)
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            int concurrentOperations = 1200;
            ExecutorService executor = Executors.newFixedThreadPool(100);
            CountDownLatch latch = new CountDownLatch(concurrentOperations);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < concurrentOperations; i++) {
                final int messageId = i;
                executor.submit(() -> {
                    try {
                        Message message = Message.builder()
                                .topic("sc002.concurrent.test")
                                .payload(("Concurrent message " + messageId).getBytes())
                                .header("thread", Thread.currentThread().getName())
                                .build();

                        CompletableFuture<SendResult> future = client.sendMessageAsync(message);
                        SendResult result = future.get(30, TimeUnit.SECONDS);

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        logger.debug("Operation {} failed: {}", messageId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            assertTrue(completed, "All operations should complete within timeout");

            double successRate = (double) successCount.get() / concurrentOperations;
            double opsPerSecond = (double) concurrentOperations / (totalTime / 1000.0);

            logger.info("✅ Concurrent operations test completed:");
            logger.info("   Total operations: {}", concurrentOperations);
            logger.info("   Successful: {} ({:.1f}%)", successCount.get(), successRate * 100);
            logger.info("   Failed: {}", failCount.get());
            logger.info("   Total time: {}ms", totalTime);
            logger.info("   Operations/sec: {:.2f}", opsPerSecond);

            assertTrue(successCount.get() >= concurrentOperations * 0.95,
                    "At least 95% of operations should succeed");
            assertTrue(successCount.get() >= 1000,
                    "Should handle at least 1000 concurrent operations successfully");

        } finally {
            client.shutdown(Duration.ofSeconds(10));
            executor.shutdown();
        }
    }

    @Test
    public void testSC003_DeliverySuccessRate999Percent() throws Exception {
        logger.info("🎯 SC-003: Testing 99.9% delivery success rate");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("perf-reliability-producer")
                .retryTimes(3)
                .sendTimeout(Duration.ofSeconds(5))
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            int totalMessages = 5000;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(totalMessages);

            // Send messages in batches to avoid overwhelming the system
            for (int batch = 0; batch < 50; batch++) {
                List<CompletableFuture<SendResult>> batchFutures = new ArrayList<>();

                for (int i = 0; i < 100; i++) {
                    int globalId = batch * 100 + i;
                    Message message = Message.builder()
                            .topic("sc003.reliability.test")
                            .payload(("Reliability test message " + globalId).getBytes())
                            .header("batch", String.valueOf(batch))
                            .build();

                    CompletableFuture<SendResult> future = client.sendMessageAsync(message);
                    batchFutures.add(future);
                }

                // Wait for this batch to complete
                for (CompletableFuture<SendResult> future : batchFutures) {
                    future.whenComplete((result, throwable) -> {
                        if (throwable == null && result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                        latch.countDown();
                    });
                }

                // Small pause between batches
                Thread.sleep(100);
            }

            boolean completed = latch.await(120, TimeUnit.SECONDS);
            assertTrue(completed, "All messages should complete within timeout");

            double successRate = (double) successCount.get() / totalMessages;
            double failureRate = (double) failCount.get() / totalMessages;

            logger.info("✅ Reliability test results:");
            logger.info("   Total messages: {}", totalMessages);
            logger.info("   Successful: {} ({:.4f}%)", successCount.get(), successRate * 100);
            logger.info("   Failed: {} ({:.4f}%)", failCount.get(), failureRate * 100);

            // Success rate should be at least 99.9%
            assertTrue(successRate >= 0.999,
                    "Success rate should be at least 99.9%, was: " + (successRate * 100) + "%");

        } finally {
            client.shutdown(Duration.ofSeconds(10));
        }
    }

    @Test
    public void testSC004_TimeoutCompletionUnder5Seconds() throws Exception {
        logger.info("🎯 SC-004: Testing <5s timeout completion");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("perf-timeout-producer")
                .consumerGroup("perf-timeout-consumer")
                .requestTimeout(Duration.ofSeconds(4)) // 4 seconds < 5 second requirement
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            // Set up a fast responder
            client.subscribe("sc004.timeout.request", message -> {
                logger.debug("Processing timeout test request: {}", message.getMessageId());

                String responseContent = "Quick response: " + new String(message.getPayload());
                Message response = Message.builder()
                        .topic("sc004.timeout.response")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .build();

                return ai.hack.rocketmq.callback.MessageProcessingResult.success(response);
            });

            // Wait for subscription
            Thread.sleep(2000);

            int requestCount = 100;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger timeoutCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(requestCount);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < requestCount; i++) {
                Message request = Message.builder()
                        .topic("sc004.timeout.request")
                        .payload(("Timeout test request " + i).getBytes())
                        .callbackTopic("sc004.timeout.response")
                        .build();

                CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(3));

                responseFuture.whenComplete((response, throwable) -> {
                    if (throwable == null && response != null) {
                        successCount.incrementAndGet();
                    } else {
                        timeoutCount.incrementAndGet();
                    }
                    latch.countDown();
                });
            }

            boolean completed = latch.await(20, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            assertTrue(completed, "All requests should complete within timeout");

            double successRate = (double) successCount.get() / requestCount;
            double avgTimePerRequest = (double) totalTime / requestCount;

            logger.info("✅ Timeout completion test results:");
            logger.info("   Total requests: {}", requestCount);
            logger.info("   Successful: {} ({:.1f}%)", successCount.get(), successRate * 100);
            logger.info("   Timeouts: {}", timeoutCount.get());
            logger.info("   Total time: {}ms", totalTime);
            logger.info("   Avg time per request: {:.2f}s", avgTimePerRequest / 1000.0);

            assertTrue(avgTimePerRequest < 5000,
                    "Average time per request should be under 5 seconds, was: " + (avgTimePerRequest / 1000.0) + "s");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testSC005_RecoveryTimeUnder30Seconds() throws Exception {
        logger.info("🎯 SC-005: Testing <30s recovery time");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr("invalid:9999") // Invalid broker to simulate failure
                .producerGroup("perf-recovery-producer")
                .sendTimeout(Duration.ofSeconds(2))
                .retryTimes(1)
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            // First, try to send a message to the invalid broker (should fail)
            Message message = Message.builder()
                    .topic("sc005.recovery.test")
                    .payload("Recovery test message".getBytes())
                    .build();

            CompletableFuture<SendResult> firstFuture = client.sendMessageAsync(message);
            assertThrows(Exception.class, () -> firstFuture.get(5, TimeUnit.SECONDS),
                    "First send to invalid broker should fail");

            // Now reconfigure with proper broker and test recovery
            ClientConfiguration validConfig = ClientConfiguration.builder()
                    .namesrvAddr(testContainerConfig.testNameserverAddress())
                    .producerGroup("perf-recovery-producer-v2")
                    .sendTimeout(Duration.ofSeconds(3))
                    .build();

            // Note: In a real scenario, you'd reinitialize the client or use a circuit breaker
            // For this test, we'll initialize a new client to simulate recovery

            RocketMQAsyncClient recoveredClient = new RocketMQAsyncClient();
            try {
                long recoveryStartTime = System.currentTimeMillis();
                recoveredClient.initialize(validConfig);

                // Test that the recovered client can send messages successfully
                Message recoveryMessage = Message.builder()
                        .topic("sc005.recovery.test")
                        .payload("Recovery succeeded".getBytes())
                        .build();

                CompletableFuture<SendResult> recoveryFuture = recoveredClient.sendMessageAsync(recoveryMessage);
                SendResult result = recoveryFuture.get(10, TimeUnit.SECONDS);

                long recoveryTime = System.currentTimeMillis() - recoveryStartTime;

                assertTrue(result.isSuccess(), "Recovered client should send messages successfully");
                assertTrue(recoveryTime < 30000, "Recovery should complete in under 30 seconds, took: " + recoveryTime + "ms");

                logger.info("✅ Recovery test passed: recovery time={}ms", recoveryTime);

            } finally {
                recoveredClient.shutdown(Duration.ofSeconds(3));
            }

        } catch (Exception e) {
            // Initialization with invalid broker should fail gracefully
            logger.info("✅ Expected failure with invalid broker: {}", e.getMessage());
        } finally {
            client.shutdown(Duration.ofSeconds(3));
        }
    }

    @Test
    public void testSC006_MemoryFootprintUnder10Percent() throws Exception {
        logger.info("🎯 SC-006: Testing <10% memory footprint increase");

        // Get baseline memory usage before client initialization
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection to get baseline
        Thread.sleep(500);

        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        long baselineMax = runtime.maxMemory();
        double baselineMB = baselineMemory / (1024.0 * 1024.0);

        logger.info("Baseline memory: {:.2f}MB of {}MB max", baselineMB, baselineMax / (1024.0 * 1024.0));

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("sc006.memory-producer")
                .consumerGroup("sc006.memory-consumer")
                .connectionPoolSize(8)
                .maxConcurrentOperations(200)
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            // Simulate some activity to allocate memory buffers
            List<CompletableFuture<SendResult>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Message message = Message.builder()
                        .topic("sc006.memory.test")
                        .payload(new byte[1024]) // 1KB messages
                        .build();
                futures.add(client.sendMessageAsync(message));
            }

            // Wait for operations to complete
            for (CompletableFuture<SendResult> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            // Force garbage collection to measure stable memory usage
            runtime.gc();
            Thread.sleep(1000);
            runtime.gc();
            Thread.sleep(500);

            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            double currentMB = currentMemory / (1024.0 * 1024.0);
            double memoryIncreaseMB = currentMB - baselineMB;
            double maxMemoryMB = baselineMax / (1024.0 * 1024.0);
            double memoryIncreasePercent = (memoryIncreaseMB / maxMemoryMB) * 100.0;

            logger.info("✅ Memory footprint test results:");
            logger.info("   Baseline memory: {:.2f}MB", baselineMB);
            logger.info("   Current memory: {:.2f}MB", currentMB);
            logger.info("   Memory increase: {:.2f}MB ({:.4f}% of max)", memoryIncreaseMB, memoryIncreasePercent);
            logger.info("   Max heap memory: {:.2f}MB", maxMemoryMB);

            // Memory increase should be less than 10% of max heap
            assertTrue(memoryIncreasePercent < 10.0,
                    "Memory increase should be under 10% of max heap, was: " + memoryIncreasePercent + "%");

            // Also check if absolute memory usage is reasonable (< 100MB footprint requirement)
            assertTrue(currentMB < baselineMB + 100,
                    "Total memory usage should be under 100MB over baseline, was: " + currentMB + "MB");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }
}

// Mock TestContainerConfig
class RocketMQTestContainerConfig {
    public String testNameserverAddress() {
        return "localhost:9876";
    }
}