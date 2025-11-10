/**
 * RocketMQ Async Client Library
 *
 * This package provides an enterprise-grade asynchronous RocketMQ client library with support for:
 * - Async message publishing with FIFO ordering
 * - Async message consumption with callbacks
 * - Request-response patterns with timeout handling
 * - High-concurrency message handling
 * - TLS security and authentication
 * - Local persistence with RocksDB + H2
 * - Comprehensive monitoring and metrics
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link ai.hack.rocketmq.RocketMQAsyncClient} - Main client interface</li>
 *   <li>{@link ai.hack.rocketmq.config.ClientConfiguration} - Configuration management</li>
 *   <li>{@link ai.hack.rocketmq.model.Message} - Message entity</li>
 *   <li>{@link ai.hack.rocketmq.callback.MessageCallback} - Callback interface</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * ClientConfiguration config = ClientConfiguration.builder()
 *     .namesrvAddr("localhost:9876")
 *     .producerGroup("my-producer")
 *     .enableTls(true)
 *     .build();
 *
 * RocketMQAsyncClient client = new DefaultRocketMQAsyncClient();
 * client.initialize(config);
 *
 * Message message = Message.builder()
 *     .topic("user.events")
 *     .payload("Hello World".getBytes())
 *     .build();
 *
 * client.sendMessageAsync(message)
 *     .thenAccept(result -> System.out.println("Sent: " + result.getMessageId()));
 * }</pre>
 *
 * @version 1.0.0
 * @since 2025-11-09
 */
package ai.hack.rocketmq;