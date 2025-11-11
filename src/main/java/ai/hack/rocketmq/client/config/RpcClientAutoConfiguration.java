package ai.hack.rocketmq.client.config;

import ai.hack.rocketmq.client.RpcClient;
import ai.hack.rocketmq.client.RpcClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot Auto-Configuration for RocketMQ RPC Client.
 * <p>
 * This configuration class automatically creates and configures an RpcClient bean
 * when RocketMQ client classes are present on the classpath and the configuration
 * properties are properly set.
 * <p>
 * <strong>Auto-Configuration Conditions:</strong>
 * <ul>
 *   <li>RocketMQ client classes must be present on classpath</li>
 *   <li>No existing RpcClient bean is defined by user</li>
 *   <li>Configuration property "rocketmq.rpc.client.enabled" is not false</li>
 * </ul>
 * <p>
 * <strong>Configuration Properties:</strong>
 * <p>See {@link RpcClientConfig} for available configuration options under prefix
 * "rocketmq.rpc.client".
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>{@code
 * # application.yml
 * rocketmq:
 *   rpc:
 *     client:
 *       enabled: true
 *       broker-url: localhost:9876
 *       request-topic: RPC_REQUEST
 *       default-timeout-millis: 30000
 *       max-concurrent-requests: 1000
 * }</pre>
 * <p>
 * <strong>Manual Bean Definition:</strong>
 * <p>If you need custom configuration, you can define your own RpcClient bean:
 * <pre>{@code
 * @Configuration
 * public class MyRpcConfig {
 *     @Bean
 *     public RpcClient rpcClient(RpcClientConfig config) {
 *         RpcClient client = new RpcClientImpl(config);
 *         client.start();
 *         return client;
 *     }
 * }
 * }</pre>
 *
 * @author Claude Code
 * @since 1.0.0
 * @see RpcClientConfig
 * @see RpcClient
 * @see RpcClientImpl
 */
@AutoConfiguration
@ConditionalOnClass(name = {
    "org.apache.rocketmq.client.producer.DefaultMQProducer",
    "org.apache.rocketmq.client.consumer.DefaultMQPushConsumer"
})
@ConditionalOnProperty(
    prefix = "rocketmq.rpc.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(RpcClientConfig.class)
public class RpcClientAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(RpcClientAutoConfiguration.class);

    /**
     * Creates and starts an RpcClient bean if not already defined.
     * <p>
     * The client is automatically started after construction. Spring will manage
     * its lifecycle, calling the {@code close()} method (annotated with @PreDestroy)
     * when the application context shuts down.
     *
     * @param config the RPC client configuration properties
     * @return configured and started RpcClient instance
     * @throws ai.hack.rocketmq.client.exception.RpcException if client cannot be started
     */
    @Bean
    @ConditionalOnMissingBean(RpcClient.class)
    public RpcClient rpcClient(RpcClientConfig config) {
        logger.info("Auto-configuring RocketMQ RPC Client with broker: {}", config.getBrokerUrl());
        logger.debug("RPC Client Configuration: requestTopic={}, defaultTimeout={}ms, maxConcurrentRequests={}, maxConcurrentSessions={}",
            config.getRequestTopic(),
            config.getDefaultTimeoutMillis(),
            config.getMaxConcurrentRequests(),
            config.getMaxConcurrentSessions());

        try {
            RpcClient client = new RpcClientImpl(config);
            client.start();

            logger.info("RocketMQ RPC Client auto-configured and started successfully");
            return client;

        } catch (Exception e) {
            logger.error("Failed to auto-configure RocketMQ RPC Client", e);
            throw new IllegalStateException("Failed to start RPC Client during auto-configuration", e);
        }
    }
}
