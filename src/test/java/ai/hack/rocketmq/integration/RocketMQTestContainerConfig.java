package ai.hack.rocketmq.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.RocketMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainers configuration for RocketMQ integration tests.
 * Provides an embedded RocketMQ broker for testing scenarios.
 */
@TestConfiguration
public class RocketMQTestContainerConfig {

    /**
     * Creates a RocketMQ container for integration testing.
     * Uses the official RocketMQ 5.3.3 image.
     */
    @Bean
    @Primary
    public RocketMQContainer rocketMQContainer() {
        return new RocketMQContainer(DockerImageName.parse("apache/rocketmq:5.3.3"))
                .withNameserverPort(9876)
                .withExposedPorts(9876, 10911)
                .waitingFor(Wait.forLogMessage(".*The Name Server boot success.*", 1));
    }

    /**
     * Provides the NameServer address for connecting to the test container.
     */
    @Bean
    @Primary
    public String testNameserverAddress() {
        return rocketMQContainer().getNameserverAddress();
    }
}