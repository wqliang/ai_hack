package ai.hack.rocketmq.quickstart;

import ai.hack.rocketmq.RocketMQAsyncClient;
import ai.hack.rocketmq.DefaultRocketMQAsyncClient;
import ai.hack.rocketmq.callback.MessageCallback;
import ai.hack.rocketmq.callback.MessageProcessingResult;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Quick Start Guide examples and ensures the library works as documented.
 * This test covers all the basic usage patterns from the quickstart.md guide.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "rocketmq.client.namesrv-addr=localhost:9876",
        "rocketmq.client.consumer-group=quickstart-consumer",
        "rocketmq.client.producer-group=quickstart-producer",
        "rocketmq.client.request-timeout=5s",
        "rocketmq.client.send-timeout=3s",
        "rocketmq.client.retry-times=2"
})
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
public class QuickStartValidationTest {

    private static final Logger logger = LoggerFactory.getLogger(QuickStartValidationTest.class);

    @Autowired
    private RocketMQTestContainerConfig testContainerConfig;

    @Test
    public void testBasicAsyncMessagePublishing() throws Exception {
        logger.info("🚀 Testing basic async message publishing as documented in quickstart");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-producer")
                .consumerGroup("quickstart-consumer")
                .sendTimeout(Duration.ofSeconds(5))
                .retryTimes(2)
                .build();

        RocketMQAsyncClient client = new RocketMQAsyncClient();
        try {
            client.initialize(config);

            // Basic message publishing example from quickstart
            String messageContent = "Hello RocketMQ from Quick Start! " + System.currentTimeMillis();
            Message message = Message.builder()
                    .topic("quickstart.topic")
                    .payload(messageContent.getBytes())
                    .header("source", "quickstart-test")
                    .build();

            // Asynchronous send
            CompletableFuture<SendResult> future = client.sendMessageAsync(message);
            SendResult result = future.get(10, TimeUnit.SECONDS);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertNotNull(result.getMessageId());
            assertEquals("quickstart.topic", result.getTopic());

            logger.info("✅ Basic async publishing validated: {}", result.getMessageId());

        } finally {
            client.shutdown(Duration.ofSeconds(3));
        }
    }

    @Test
    public void testMessageConsumptionWithCallback() throws Exception {
        logger.info("📥 Testing message consumption with callback as documented");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-producer")
                .consumerGroup("quickstart-consumer")
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Message consumption setup from quickstart
            CountDownLatch messageReceived = new CountDownLatch(1);
            AtomicInteger processedMessages = new AtomicInteger(0);

            client.subscribe("quickstart.consume", message -> {
                try {
                    String content = new String(message.getPayload());
                    logger.info("📨 Received message: {}", content);
                    processedMessages.incrementAndGet();
                    messageReceived.countDown();

                    return MessageProcessingResult.success();
                } catch (Exception e) {
                    logger.error("Error processing message", e);
                    return MessageProcessingResult.failure("Processing error", e);
                }
            });

            // Wait for subscription to establish
            Thread.sleep(2000);

            // Send test message
            String testContent = "Test message for Quick Start consumption";
            Message testMessage = Message.builder()
                    .topic("quickstart.consume")
                    .payload(testContent.getBytes())
                    .build();

            CompletableFuture<SendResult> sendFuture = client.sendMessageAsync(testMessage);
            SendResult sendResult = sendFuture.get(5, TimeUnit.SECONDS);
            assertTrue(sendResult.isSuccess());

            // Wait for message to be consumed
            boolean received = messageReceived.await(10, TimeUnit.SECONDS);
            assertTrue(received, "Message should be received and processed");
            assertEquals(1, processedMessages.get());

            logger.info("✅ Message consumption with callback validated");

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testRequestResponsePattern() throws Exception {
        logger.info("🔄 Testing request-response pattern as documented");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-producer")
                .consumerGroup("quickstart-consumer")
                .requestTimeout(Duration.ofSeconds(8))
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // Setup responder for request-response
            client.subscribe("quickstart.request", message -> {
                String requestContent = new String(message.getPayload());
                logger.info("🤖 Processing request: {}", requestContent);

                String responseContent = "Echo: " + requestContent;
                Message response = Message.builder()
                        .topic("quickstart.response")
                        .payload(responseContent.getBytes())
                        .header("response-to", message.getMessageId())
                        .build();

                return MessageProcessingResult.success(response);
            });

            // Wait for subscription
            Thread.sleep(2000);

            // Send request and wait for response
            String requestContent = "Hello from Quick Start!";
            Message request = Message.builder()
                    .topic("quickstart.request")
                    .payload(requestContent.getBytes())
                    .callbackTopic("quickstart.response")
                    .build();

            CompletableFuture<Message> responseFuture = client.sendAndReceiveAsync(request, Duration.ofSeconds(6));
            Message response = responseFuture.get(10, TimeUnit.SECONDS);

            assertNotNull(response);
            assertEquals("quickstart.response", response.getTopic());
            String responseContent = new String(response.getPayload());
            assertEquals("Echo: " + requestContent, responseContent);

            logger.info("✅ Request-response pattern validated: {}", responseContent);

        } finally {
            client.shutdown(Duration.ofSeconds(5));
        }
    }

    @Test
    public void testConfigurationFromQuickstart() throws Exception {
        logger.info("⚙️ Testing configuration examples from quickstart");

        // Test configuration building as shown in quickstart
        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-config-producer")
                .consumerGroup("quickstart-config-consumer")
                .maxMessageSize(4 * 1024 * 1024) // 4MB
                .sendTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(10))
                .retryTimes(3)
                .compressionEnabled(true)
                .orderedProcessing(true)
                .connectionPoolSize(16)
                .maxConcurrentOperations(500)
                .enableBackpressure(true)
                .backpressureThreshold(0.8)
                .build();

        // Verify configuration values
        assertEquals(4 * 1024 * 1024, config.getMaxMessageSize());
        assertEquals(Duration.ofSeconds(5), config.getSendTimeout());
        assertEquals(Duration.ofSeconds(10), config.getRequestTimeout());
        assertEquals(3, config.getRetryTimes());
        assertTrue(config.isCompressionEnabled());
        assertTrue(config.isOrderedProcessing());
        assertEquals(16, config.getConnectionPoolSize());
        assertEquals(500, config.getMaxConcurrentOperations());
        assertTrue(config.isBackpressureEnabled());
        assertEquals(0.8, config.getBackpressureThreshold());

        logger.info("✅ Configuration builder validation passed");
    }

    @Test
    public void testTLSConfiguration() throws Exception {
        logger.info("🔐 Testing TLS configuration from quickstart");

        // TLS configuration example (simulation)
        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-tls-producer")
                .consumerGroup("quickstart-tls-consumer")
                .enableTls(true)
                .authentication("test-access-key", "test-secret-key")
                .trustStorePath("/path/to/truststore.jks")
                .build();

        // Verify TLS settings
        assertTrue(config.isTlsEnabled());
        assertEquals("test-access-key", config.getAccessKey());
        assertEquals("test-secret-key", config.getSecretKey());

        logger.info("✅ TLS configuration validation passed");
    }

    @Test
    public void testErrorHandling() throws Exception {
        logger.info("❌ Testing error handling as documented");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr("invalid:9999") // Invalid address to test error handling
                .producerGroup("quickstart-error-producer")
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
        try {
            client.initialize(config);

            // This should fail with proper error handling
            Message message = Message.builder()
                    .topic("test.topic")
                    .payload("Test message".getBytes())
                    .build();

            CompletableFuture<SendResult> future = client.sendMessageAsync(message);

            // Expect failure due to invalid broker address
            assertThrows(Exception.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            });

            logger.info("✅ Error handling validation passed - client properly handled invalid configuration");

        } catch (Exception e) {
            // Initialization should also fail gracefully
            logger.info("✅ Error handling validation passed - initialization failed gracefully: {}", e.getMessage());
        } finally {
            try {
                client.shutdown(Duration.ofSeconds(2));
            } catch (Exception e) {
                // Expected during cleanup
            }
        }
    }

    @Test
    public void testClientLifecycle() throws Exception {
        logger.info("🔄 Testing client lifecycle management");

        ClientConfiguration config = ClientConfiguration.builder()
                .namesrvAddr(testContainerConfig.testNameserverAddress())
                .producerGroup("quickstart-lifecycle-producer")
                .consumerGroup("quickstart-lifecycle-consumer")
                .build();

        RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();

        // Test initialization
        client.initialize(config);
        assertNotNull(client.getClientStatus());
        assertTrue(client.getClientStatus().isOperational());

        // Test simple operation
        Message message = Message.builder()
                .topic("lifecycle.test")
                .payload("Lifecycle test message".getBytes())
                .build();

        CompletableFuture<SendResult> future = client.sendMessageAsync(message);
        SendResult result = future.get(5, TimeUnit.SECONDS);
        assertNotNull(result);

        // Test shutdown
        client.shutdown(Duration.ofSeconds(3));
        logger.info("✅ Client lifecycle management validated");

        // Verify client is properly shutdown (should not throw exception)
        assertDoesNotThrow(() -> {
            client.shutdown(Duration.ofSeconds(1));
        });
    }
}

// Mock TestContainerConfig for test environment
class RocketMQTestContainerConfig {
    public String testNameserverAddress() {
        return "localhost:9876";
    }
}