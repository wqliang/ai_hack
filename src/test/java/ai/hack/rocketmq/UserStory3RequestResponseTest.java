package ai.hack.rocketmq;

import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.callback.MessageProcessingResult;
import ai.hack.rocketmq.config.ClientConfiguration;
import ai.hack.rocketmq.exception.TimeoutException;
import ai.hack.rocketmq.model.Message;
import ai.hack.rocketmq.result.SendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for User Story 3 - Request-Response Pattern with Timeout.
 * Tests the complete request-response correlation and timeout handling functionality.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client.consumer-group=test-rr-consumer",
        "rocketmq.client.producer-group=test-rr-producer",
        "rocketmq.client.request-timeout=5s",
        "rocketmq.client.send-timeout=3s",
        "rocketmq.client.retry-times=2",
        "rocketmq.client.max-consume-threads=4"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class UserStory3RequestResponseTest {

    private static final Logger logger = LoggerFactory.getLogger(UserStory3RequestResponseTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testBasicRequestResponseWithTimeout() throws Exception {
        logger.info("Testing basic request-response pattern with timeout");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-rr-consumer")
                .producerGroup("test-rr-producer")
                .requestTimeout(Duration.ofSeconds(10)) // Longer for test
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .maxConsumeThreads(4)
                .enablePersistence(true)
                .persistence("./test-data-rr", Duration.ofSeconds(1))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Prepare for request-response testing
            AtomicInteger requestCount = new AtomicInteger(0);
            AtomicReference<String> lastRequestContent = new AtomicReference<>();

            // Set up a responder callback
            client.subscribe("user.queries.request", message -> {
                String requestContent = new String(message.getPayload());
                logger.info("📨 Received request: {}", requestContent);

                requestCount.incrementAndGet();
                lastRequestContent.set(requestContent);

                // Create response message
                String responseContent = "Response to: " + requestContent + " at " + System.currentTimeMillis();
                Message response = Message.builder()
                        .topic("user.queries.response")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .header("request-count", String.valueOf(requestCount.get()))
                        .build();

                logger.info("📤 Sending response: {}", responseContent);

                return MessageProcessingResult.success(response);
            });

            // Wait for subscription to establish
            Thread.sleep(3000);

            // Send request and wait for response
            String requestContent = "What time is it now? " + System.currentTimeMillis();
            Message request = Message.builder()
                    .topic("user.queries.request")
                    .payload(requestContent.getBytes())
                    .callbackTopic("user.queries.response")
                    .header("request-id", "req-001")
                    .build();

            logger.info("📤 Sending request: {}", request.getMessageId());

            long startTime = System.currentTimeMillis();

            // Use sendAndReceiveAsync (this is the core functionality being tested)
            CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(8));

            try {
                Message response = responseFuture.get(15, TimeUnit.SECONDS);
                long responseTime = System.currentTimeMillis() - startTime;

                assertNotNull(response);
                assertEquals("user.queries.response", response.getTopic());
                assertEquals("Response to: " + requestContent, new String(response.getPayload()));
                assertEquals(requestCount.get(), Integer.parseInt(response.getHeader("request-count")));

                logger.info("✅ Request-response completed in {}ms", responseTime);
                logger.info("✅ Response content: {}", new String(response.getPayload()));

                // Verify timeout behavior - the request should complete before timeout
                assertTrue(responseTime < 8000, "Request should complete well before 8-second timeout");

                // Verify counts
                assertEquals(1, requestCount.get());
                assertEquals(requestContent, lastRequestContent.get());

            } catch (JavaTimeoutException e) {
                fail("Request should not timeout - response should be received");
            }

            // Check client status
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testRequestResponseTimeoutHandling() throws Exception {
        logger.info("Testing request-response timeout handling");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-timeout-consumer")
                .producerGroup("test-timeout-producer")
                .requestTimeout(Duration.ofSeconds(3)) // Short timeout for testing
                .sendTimeout(Duration.ofSeconds(2))
                .retryTimes(1)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Set up a slow responder that intentionally delays响应
            client.subscribe("slow.queries.request", message -> {
                logger.info("📨 Received slow request: {}", message.getMessageId());

                try {
                    // Intentionally delay longer than timeout
                    Thread.sleep(5000); // 5 seconds > 3-second timeout

                    String requestContent = new String(message.getPayload());
                    String responseContent = "Delayed response to: " + requestContent;

                    Message response = Message.builder()
                            .topic("slow.queries.response")
                            .payload(responseContent.getBytes())
                            .header("response-to", message.getMessageId())
                            .build();

                    return MessageProcessingResult.success(response);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return MessageProcessingResult.failure("Interrupted", e);
                }
            });

            // Wait for subscription to establish
            Thread.sleep(2000);

            // Send request that will timeout
            String requestContent = "Quick request expecting timeout";
            Message request = Message.builder()
                    .topic("slow.queries.request")
                    .payload(requestContent.getBytes())
                    .callbackTopic("slow.queries.response")
                    .build();

            logger.info("📤 Sending request that should timeout: {}", request.getMessageId());

            long startTime = System.currentTimeMillis();

            // Use sendAndReceiveAsync with shorter timeout than server response
            CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(2));

            try {
                Message response = responseFuture.get(15, TimeUnit.SECONDS);
                long responseTime = System.currentTimeMillis() - startTime;

                // If we get here, the request completed (shouldn't happen with our timing)
                logger.warn("❌ Unexpected: Request completed in {}ms - should have timed out", responseTime);
                fail("Request should have timed out based on our configuration");

            } catch (Exception e) {
                long failTime = System.currentTimeMillis() - startTime;
                logger.info("✅ Request correctly timed out after {}ms", failTime);

                // Verify it's a timeout exception
                assertTrue(e.getCause() instanceof TimeoutException,
                        "Should receive TimeoutException, got: " + e.getCause().getClass().getSimpleName());

                TimeoutException timeoutException = (TimeoutException) e.getCause();
                assertEquals("request-response", timeoutException.getOperation());
                assertTrue(timeoutException.getTimeout().getSeconds() <= 3,
                        "Timeout should be around configured limit");

                logger.info("✅ Timeout exception details: {}", timeoutException);
            }

            // Verify client is still operational after timeout
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testConcurrentRequestResponseCorrelation() throws Exception {
        logger.info("Testing concurrent request-response correlation");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-concurrent-consumer")
                .producerGroup("test-concurrent-producer")
                .requestTimeout(Duration.ofSeconds(10))
                .sendTimeout(Duration.ofSeconds(3))
                .retryTimes(2)
                .maxConsumeThreads(8)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            AtomicInteger responsesGenerated = new AtomicInteger(0);

            // Set up responder that generates unique responses
            client.subscribe("concurrent.test.request", message -> {
                String requestContent = new String(message.getPayload());
                logger.info("📨 Received concurrent request: {}", requestContent);

                int requestId = responsesGenerated.incrementAndGet();

                // Create unique response
                String responseContent = "Response-" + requestId + " for: " + requestContent;
                Message response = Message.builder()
                        .topic("concurrent.test.response")
                        .payload(responseContent.getBytes())
                        .header("request-id", String.valueOf(requestId))
                        .header("response-to", message.getMessageId())
                        .build();

                return MessageProcessingResult.success(response);
            });

            // Wait for subscription to establish
            Thread.sleep(3000);

            // Send multiple concurrent requests
            int numRequests = 10;
            CompletableFuture<Message>[] responseFutures = new CompletableFuture[numRequests];
            CountDownLatch allComplete = new CountDownLatch(numRequests);
            AtomicInteger successfulResponses = new AtomicInteger(0);
            AtomicInteger failedResponses = new AtomicInteger(0);

            for (int i = 0; i < numRequests; i++) {
                final int requestId = i;
                String requestContent = "Concurrent request #" + i;

                Message request = Message.builder()
                        .topic("concurrent.test.request")
                        .payload(requestContent.getBytes())
                        .callbackTopic("concurrent.test.response")
                        .header("request-index", String.valueOf(i))
                        .build();

                CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(8));

                responseFutures[i] = responseFuture;

                responseFuture.whenComplete((response, throwable) -> {
                    allComplete.countDown();
                    if (throwable != null) {
                        failedResponses.incrementAndGet();
                        logger.error("❌ Request {} failed: {}", requestId, throwable.getMessage());
                    } else {
                        successfulResponses.incrementAndGet();
                        logger.info("✅ Request {} completed: {}", requestId, new String(response.getPayload()));
                    }
                });
            }

            // Wait for all requests to complete
            boolean allCompleted = allComplete.await(20, TimeUnit.SECONDS);
            assertTrue(allCompleted, "All concurrent requests should complete within 20 seconds");

            // Verify results
            assertEquals(numRequests, successfulResponses.get() + failedResponses.get());
            assertTrue(successfulResponses.get() > 0, "At least some requests should succeed");

            // Check client status
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());

            logger.info("✅ Concurrent request-response test completed: {} successful, {} failed",
                       successfulResponses.get(), failedResponses.get());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testRequestResponseWithCustomTopics() throws Exception {
        logger.info("Testing request-response with custom topic naming");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-custom-consumer")
                .producerGroup("test-custom-producer")
                .requestTimeout(Duration.ofSeconds(8))
                .sendTimeout(Duration.ofSeconds(3))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Use .request/.response pattern
            client.subscribe("custom.api.request", message -> {
                String requestContent = new String(message.getPayload());
                logger.info("📨 Received custom API request: {}", requestContent);

                // Create response to corresponding .response topic
                String responseContent = "Custom API response: " + requestContent;
                Message response = Message.builder()
                        .topic("custom.api.response")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .header("api-version", "1.0")
                        .build();

                return MessageProcessingResult.success(response);
            });

            // Wait for subscription to establish
            Thread.sleep(2000);

            // Send request to .request topic
            String requestContent = "Get user data for ID: 12345";
            Message request = Message.builder()
                    .topic("custom.api.request")
                    .payload(requestContent.getBytes())
                    .callbackTopic("custom.api.response")
                    .header("api-key", "test-key-123")
                    .build();

            logger.info("📤 Sending request to custom API: {}", request.getMessageId());

            // The client should automatically handle the response topic subscription
            CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(10));

            Message response = responseFuture.get(15, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("custom.api.response", response.getTopic());
            assertEquals("Custom API response: " + requestContent, new String(response.getPayload()));
            assertEquals("1.0", response.getHeader("api-version"));

            logger.info("✅ Custom topic request-response completed");
            logger.info("✅ Response: {}", new String(response.getPayload()));

            // Verify client has the right subscriptions
            ClientStatus status = client.getClientStatus();
            assertTrue(status.getSubscribedTopics().contains("custom.api.request"));
            // Note: The response topic might also be auto-subscribed

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testErrorHandlingInRequestResponse() throws Exception {
        logger.info("Testing error handling in request-response pattern");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-error-consumer")
                .producerGroup("test-error-producer")
                .requestTimeout(Duration.ofSeconds(5))
                .sendTimeout(Duration.ofSeconds(3))
                .retryTimes(2)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Set up a responder that sometimes fails
            AtomicInteger requestCount = new AtomicInteger(0);

            client.subscribe("error.test.request", message -> {
                int count = requestCount.incrementAndGet();
                String requestContent = new String(message.getPayload());
                logger.info("📨 Received error test request #{}: {}", count, requestContent);

                // Fail every second request
                if (count % 2 == 0) {
                    logger.warn("🚨 Simulating failure for request #{}", count);
                    return MessageProcessingResult.failure("Simulated server error", new RuntimeException("Test error"));
                } else {
                    // Succeed odd-numbered requests
                    String responseContent = "Success response # " + count;
                    Message response = Message.builder()
                            .topic("error.test.response")
                            .payload(responseContent.getBytes())
                            .header("response-to", message.getMessageId())
                            .header("request-number", String.valueOf(count))
                            .build();

                    return MessageProcessingResult.success(response);
                }
            });

            // Wait for subscription to establish
            Thread.sleep(3000);

            // Test successful request
            logger.info("📤 Testing successful request (should succeed)");
            Message successRequest = Message.builder()
                    .topic("error.test.request")
                    .payload("Successful request".getBytes())
                    .callbackTopic("error.test.response")
                    .build();

            CompletableFuture<Message> successFuture = client.sendAndReceiveAsync(successRequest, Duration.ofSeconds(5));
            Message successResponse = successFuture.get(10, TimeUnit.SECONDS);

            assertNotNull(successResponse);
            assertTrue(new String(successResponse.getPayload()).contains("Success response # 1"));
            logger.info("✅ Successful request completed: {}", new String(successResponse.getPayload()));

            // Test failed request (this won't get a response, so it should timeout)
            logger.info("📤 Testing failed request (should fail at server and timeout)");
            Message failRequest = Message.builder()
                    .topic("error.test.request")
                    .payload("Failing request".getBytes())
                    .callbackTopic("error.test.response")
                    .build();

            CompletableFuture<Message> failFuture = client.sendAndReceiveAsync(failRequest, Duration.ofSeconds(3));

            try {
                Message failResponse = failFuture.get(6, TimeUnit.SECONDS);
                fail("Request should have failed or timed out");
            } catch (Exception e) {
                logger.info("✅ Request correctly failed (server-side error): {}", e.getMessage());
                // This is expected - server-side failure means no response
            }

            // Test a subsequent successful request to ensure system recovers
            logger.info("📤 Testing recovery request (should succeed again)");
            Message recoveryRequest = Message.builder()
                    .topic("error.test.request")
                    .payload("Recovery request".getBytes())
                    .callbackTopic("error.test.response")
                    .build();

            CompletableFuture<Message> recoveryFuture = client.sendAndReceiveAsync(recoveryRequest, Duration.ofSeconds(5));
            Message recoveryResponse = recoveryFuture.get(10, TimeUnit.SECONDS);

            assertNotNull(recoveryResponse);
            assertTrue(new String(recoveryResponse.getPayload()).contains("Success response # 3"));
            logger.info("✅ Recovery request completed: {}", new String(recoveryResponse.getPayload()));

            // Verify client is still healthy
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }
}