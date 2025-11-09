package ai.hack.rocketmq;

import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.callback.MessageProcessingResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for User Story 2 - Async Message Consumption with Callback.
 * Tests the complete message consumption and callback processing functionality.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client consumer-group=test-consumer-group",
        "rocketmq.client producer-group=test-producer-group",
        "rocketmq.client.send-timeout=3s",
        "rocketmq.client.retry-times=2",
        "rocketmq.client.max-consume-threads=4"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class UserStory2AsyncConsumeTest {

    private static final Logger logger = LoggerFactory.getLogger(UserStory2AsyncConsumeTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testAsyncMessageConsumptionWithCallback() throws Exception {
        logger.info("Testing async message consumption with callback scenario");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-callback-consumer")
                .producerGroup("test-callback-producer")
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .maxConsumeThreads(8)
                .orderedProcessing(false)
                .enablePersistence(true)
                .persistence("./test-data-consume", Duration.ofSeconds(1))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Prepare for callback testing
            CountDownLatch messageReceivedLatch = new CountDownLatch(1);
            AtomicReference<Message> receivedMessage = new AtomicReference<>();
            AtomicInteger callbackCount = new AtomicInteger(0);

            // Subscribe to test topic with callback
            client.subscribe("test.callback.consume", message -> {
                logger.info("📨 Received message in callback: {}", message.getMessageId());
                logger.info("📨 Message content: {}", new String(message.getPayload()));

                receivedMessage.set(message);
                callbackCount.incrementAndGet();

                // Return success result
                return MessageProcessingResult.success();

            });

            // Give subscription time to establish
            Thread.sleep(2000);

            // Send a message to trigger the callback
            Message message = Message.builder()
                    .topic("test.callback.consume")
                    .payload("Hello User Story 2 - Callback Test".getBytes())
                    .header("test-callback", "true")
                    .header("timestamp", System.currentTimeMillis())
                    .build();

            logger.info("📤 Sending message to trigger callback: {}", message.getMessageId());

            CompletableFuture<SendResult> sendFuture = client.sendMessageAsync(message);
            SendResult sendResult = sendFuture.get(10, TimeUnit.SECONDS);

            assertNotNull(sendResult);
            assertTrue(sendResult.isSuccess());

            // Wait for message to be received by callback
            assertTrue(messageReceivedLatch.await(15, TimeUnit.SECONDS),
                    "Message should be received by callback within 15 seconds");

            // Verify callback was called
            Message received = receivedMessage.get();
            assertNotNull(received);
            assertEquals("test.callback.consume", received.getTopic());
            assertEquals("Hello User Story 2 - Callback Test", new String(received.getPayload()));
            assertEquals(1, callbackCount.get());

            logger.info("✅ Callback executed successfully for message: {}", received.getMessageId());

            // Verify client status
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());
            assertTrue(status.getSubscribedTopics().contains("test.callback.consume"));

            logger.info("✅ Client status healthy with {} subscribed topics", status.getSubscribedTopics().size());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testCallbackWithResponseMessage() throws Exception {
        logger.info("Testing callback with automatic response message");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-response-consumer")
                .producerGroup("test-response-producer")
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .maxConsumeThreads(4)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            CountDownLatch responseReceivedLatch = new CountDownLatch(1);
            AtomicReference<Message> responseMessage = new AtomicReference<>();

            // Subscribe to request topic
            client.subscribe("test.request.topic", message -> {
                logger.info("📨 Processing request message: {}", message.getMessageId());

                String requestContent = new String(message.getPayload());
                assertTrue(requestContent.startsWith("Request:"));

                // Create response message
                String responseContent = "Response to: " + message.getMessageId() + " | " + requestContent;
                Message response = Message.builder()
                        .topic("test.response.topic")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .header("callback-type", "automatic")
                        .build();

                logger.info("📤 Sending automatic response to topic: {}", response.getTopic());

                return MessageProcessingResult.success(response);
            });

            // Subscribe to response topic for verification
            client.subscribe("test.response.topic", message -> {
                logger.info("📨 Received response message: {}", message.getMessageId());

                responseMessage.set(message);
                responseReceivedLatch.countDown();

                return MessageProcessingResult.success();
            });

            // Wait for subscriptions to establish
            Thread.sleep(3000);

            // Send request message
            Message requestMessage = Message.builder()
                    .topic("test.request.topic")
                    .payload("Request: What time is it?".getBytes())
                    .callbackTopic("test.response.topic")
                    .header("request-id", "req-001")
                    .build();

            CompletableFuture<SendResult> sendFuture = client.sendMessageAsync(requestMessage);
            SendResult sendResult = sendFuture.get(10, TimeUnit.SECONDS);
            assertTrue(sendResult.isSuccess());

            // Wait for response to be received
            assertTrue(responseReceivedLatch.await(20, TimeUnit.SECONDS),
                    "Response should be received within 20 seconds");

            Message response = responseMessage.get();
            assertNotNull(response);
            assertEquals("test.response.topic", response.getTopic());
            String responseContent = new String(response.getPayload());
            assertTrue(responseContent.contains("Response to: " + requestMessage.getMessageId()));
            assertTrue(responseContent.contains("Request: What time is it?"));

            logger.info("✅ Automatic response message working: {}", responseContent);

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testCallbackErrorHandling() throws Exception {
        logger.info("Testing callback error handling and retry logic");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-error-consumer")
                .producerGroup("test-error-producer")
                .retryTimes(3)
                .maxConsumeThreads(2)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            CountDownLatch errorOccurredLatch = new CountDownLatch(3); // Expect 3 attempts due to retry
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            // Subscribe with error-prone callback
            client.subscribe("test.error.topic", message -> {
                String content = new String(message.getPayload());

                if (errorCount.get() < 2) {
                    // Simulate error on first two attempts
                    logger.info("📨 Simulating callback error for message: {} (attempt {})", message.getMessageId(), errorCount.get() + 1);
                    errorCount.incrementAndGet();
                    errorOccurredLatch.countDown();

                    return MessageProcessingResult.failure("Simulated processing error", new RuntimeException("Test error"));
                } else {
                    // Succeed on third attempt
                    logger.info("✅ Processing message successfully on attempt: {}", errorCount.get() + 1);
                    successCount.incrementAndGet();
                    return MessageProcessingResult.success();
                }
            });

            // Wait for subscription to establish
            Thread.sleep(2000);

            // Send message that will cause callback errors initially
            Message message = Message.builder()
                    .topic("test.error.topic")
                    .payload("Test error handling".getBytes())
                    .header("error-test", "true")
                    .build();

            CompletableFuture<SendResult> sendFuture = client.sendMessageAsync(message);
            SendResult sendResult = sendFuture.get(10, TimeUnit.SECONDS);
            assertTrue(sendResult.isSuccess());

            // Wait for error attempts to occur
            assertTrue(errorOccurredLatch.await(15, TimeUnit.SECONDS),
                    "Error attempts should occur within 15 seconds");

            // Wait a bit more for successful processing
            Thread.sleep(5000);

            // Verify that retries were attempted and eventually succeeded
            assertTrue(errorCount.get() >= 2, "Should have attempted retry");
            assertTrue(successCount.get() > 0, "Should have eventually succeeded");

            logger.info("✅ Error handling working correctly - {} errors, {} successes", errorCount.get(), successCount.get());

            // Check client status
            ClientStatus status = client.getClientStatus();
            assertTrue(status.isOperational());

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testMultipleTopicCallbacks() throws Exception {
        logger.info("Testing multiple topic callbacks");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-multi-consumer")
                .producerGroup("test-multi-producer")
                .maxConsumeThreads(6)
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            CountDownLatch allMessagesReceived = new CountDownLatch(3);
            AtomicInteger topic1Count = new AtomicInteger(0);
            AtomicInteger topic2Count = new AtomicInteger(0);
            AtomicInteger topic3Count = new AtomicInteger(0);

            // Subscribe to multiple topics with different callbacks
            client.subscribe("test.multi.topic1", message -> {
                logger.info("📨 Received on topic1: {}", message.getMessageId());
                topic1Count.incrementAndGet();
                allMessagesReceived.countDown();
                return MessageProcessingResult.success();
            });

            client.subscribe("test.multi.topic2", message -> {
                logger.info("📨 Received on topic2: {}", message.getMessageId());
                topic2Count.incrementAndGet();
                allMessagesReceived.countDown();
                return MessageProcessingResult.success();
            });

            client.subscribe("test.multi.topic3", message -> {
                logger.info("📨 Received on topic3: {}", message.getMessageId());
                topic3Count.incrementAndGet();
                allMessagesReceived.countDown();
                return MessageProcessingResult.success();
            });

            // Wait for subscriptions to establish
            Thread.sleep(3000);

            // Send messages to each topic
            Message msg1 = Message.builder().topic("test.multi.topic1").payload("Message for topic 1".getBytes()).build();
            Message msg2 = Message.builder().topic("test.multi.topic2").payload("Message for topic 2".getBytes()).build();
            Message msg3 = Message.builder().topic("test.multi.topic3").payload("Message for topic 3".getBytes()).build();

            CompletableFuture<SendResult> future1 = client.sendMessageAsync(msg1);
            CompletableFuture<SendResult> future2 = client.sendMessageAsync(msg2);
            CompletableFuture<SendResult> future3 = client.sendMessageAsync(msg3);

            // Wait for all sends to complete
            SendResult result1 = future1.get(5, TimeUnit.SECONDS);
            SendResult result2 = future2.get(5, TimeUnit.SECONDS);
            SendResult result3 = future3.get(5, TimeUnit.SECONDS);

            assertTrue(result1.isSuccess());
            assertTrue(result2.isSuccess());
            assertTrue(result3.isSuccess());

            // Wait for all messages to be received by callbacks
            assertTrue(allMessagesReceived.await(15, TimeUnit.SECONDS),
                    "All messages should be received within 15 seconds");

            // Verify each topic callback was called exactly once
            assertEquals(1, topic1Count.get());
            assertEquals(1, topic2Count.get());
            assertEquals(1, topic3Count.get());

            // Verify subscribed topics in client status
            ClientStatus status = client.getClientStatus();
            assertEquals(3, status.getSubscribedTopics().size());
            assertTrue(status.getSubscribedTopics().containsAll(java.util.List.of(
                    "test.multi.topic1", "test.multi.topic2", "test.multi.topic3")));

            logger.info("✅ Multi-topic callbacks working correctly");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testConsumerLifecycleManagement() throws Exception {
        logger.info("Testing consumer lifecycle management");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .consumerGroup("test-lifecycle-consumer")
                .producerGroup("test-lifecycle-producer")
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            CountDownLatch messageLatch = new CountDownLatch(1);
            AtomicBoolean callbackExecuted = new AtomicBoolean(false);

            // Subscribe to topic
            client.subscribe("test.lifecycle.topic", message -> {
                callbackExecuted.set(true);
                messageLatch.countDown();
                return MessageProcessingResult.success();
            });

            // Wait for subscription
            Thread.sleep(2000);

            // Send test message
            Message message = Message.builder()
                    .topic("test.lifecycle.topic")
                    .payload("Lifecycle test message".getBytes())
                    .build();

            CompletableFuture<SendResult> future = client.sendMessageAsync(message);
            SendResult result = future.get(5, TimeUnit.SECONDS);
            assertTrue(result.isSuccess());

            // Wait for callback
            assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Message should be delivered");
            assertTrue(callbackExecuted.get());

            // Test unsubscribe
            client.unsubscribe("test.lifecycle.topic");

            // Wait a moment for unsubscribe to take effect
            Thread.sleep(2000);

            // Reset callback flag
            callbackExecuted.set(false);
            messageLatch = new CountDownLatch(1);

            // Send another message - should not be delivered
            Message message2 = Message.builder()
                    .topic("test.lifecycle.topic")
                    .payload("After unsubscribe".getBytes())
                    .build();

            CompletableFuture<SendResult> future2 = client.sendMessageAsync(message2);
            SendResult result2 = future2.get(5, TimeUnit.SECONDS);
            assertTrue(result2.isSuccess());

            // Wait and verify callback was NOT called (message sent successfully but no callback)
            assertFalse(messageLatch.await(5, TimeUnit.SECONDS), "Message should not be delivered after unsubscribe");
            assertFalse(callbackExecuted.get());

            logger.info("✅ Consumer lifecycle management working correctly");

            // Verify client status
            ClientStatus status = client.getClientStatus();
            assertFalse(status.getSubscribedTopics().contains("test.lifecycle.topic"));

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }
}