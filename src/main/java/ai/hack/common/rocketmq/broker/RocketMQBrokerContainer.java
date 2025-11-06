package ai.hack.common.rocketmq.broker;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocketMQ Broker 容器类
 */
public class RocketMQBrokerContainer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQBrokerContainer.class);

    private final BrokerConfig brokerConfig;
    private final NettyServerConfig nettyServerConfig;
    private final NettyClientConfig nettyClientConfig;
    private final MessageStoreConfig messageStoreConfig;
    private BrokerController brokerController;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private RocketMQBrokerContainer(Builder builder) {
        this.brokerConfig = new BrokerConfig();
        this.nettyServerConfig = new NettyServerConfig();
        this.nettyClientConfig = new NettyClientConfig();
        this.messageStoreConfig = new MessageStoreConfig();

        this.brokerConfig.setBrokerName(builder.brokerName);
        this.brokerConfig.setBrokerClusterName(builder.clusterName);
        this.brokerConfig.setBrokerId(builder.brokerId);
        this.brokerConfig.setNamesrvAddr(builder.namesrvAddr);

        this.nettyServerConfig.setListenPort(builder.listenPort);

        String storePathRootDir = builder.storePathRootDir;
        this.messageStoreConfig.setStorePathRootDir(storePathRootDir);
        this.messageStoreConfig.setStorePathCommitLog(storePathRootDir + File.separator + "commitlog");

        File storeDir = new File(storePathRootDir);
        if (!storeDir.exists()) {
            storeDir.mkdirs();
        }

        if (builder.autoCreateTopicEnable != null) {
            this.brokerConfig.setAutoCreateTopicEnable(builder.autoCreateTopicEnable);
        }

        log.info("RocketMQ Broker container initialized - name: {}, cluster: {}, port: {}, namesrv: {}",
                builder.brokerName, builder.clusterName, builder.listenPort, builder.namesrvAddr);
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            log.info("Starting RocketMQ Broker...");

            this.brokerController = new BrokerController(
                    brokerConfig,
                    nettyServerConfig,
                    nettyClientConfig,
                    messageStoreConfig
            );

            boolean initResult = brokerController.initialize();
            if (!initResult) {
                brokerController.shutdown();
                throw new RuntimeException("Failed to initialize RocketMQ Broker");
            }

            brokerController.start();

            log.info("RocketMQ Broker started successfully");
            log.info("Broker name: {}, cluster: {}, port: {}",
                    brokerConfig.getBrokerName(),
                    brokerConfig.getBrokerClusterName(),
                    nettyServerConfig.getListenPort());
        } else {
            log.warn("RocketMQ Broker is already started");
        }
    }

    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            log.info("Shutting down RocketMQ Broker...");
            if (brokerController != null) {
                brokerController.shutdown();
            }
            log.info("RocketMQ Broker stopped");
        } else {
            log.warn("RocketMQ Broker is not running");
        }
    }

    public boolean isRunning() {
        return started.get();
    }

    public int getListenPort() {
        return nettyServerConfig.getListenPort();
    }

    public String getBrokerName() {
        return brokerConfig.getBrokerName();
    }

    public String getClusterName() {
        return brokerConfig.getBrokerClusterName();
    }

    public long getBrokerId() {
        return brokerConfig.getBrokerId();
    }

    public String getNamesrvAddr() {
        return brokerConfig.getNamesrvAddr();
    }

    public String getBrokerAddress() {
        return "127.0.0.1:" + getListenPort();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String brokerName = "broker-a";
        private String clusterName = "DefaultCluster";
        private long brokerId = 0L;
        private String namesrvAddr = "127.0.0.1:9876";
        private int listenPort = 10911;
        private String storePathRootDir = System.getProperty("user.home") + File.separator + "rocketmq-broker-data";
        private Boolean autoCreateTopicEnable = true;

        public Builder brokerName(String brokerName) {
            this.brokerName = brokerName;
            return this;
        }

        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder brokerId(long brokerId) {
            this.brokerId = brokerId;
            return this;
        }

        public Builder namesrvAddr(String namesrvAddr) {
            this.namesrvAddr = namesrvAddr;
            return this;
        }

        public Builder listenPort(int listenPort) {
            this.listenPort = listenPort;
            return this;
        }

        public Builder storePathRootDir(String storePathRootDir) {
            this.storePathRootDir = storePathRootDir;
            return this;
        }

        public Builder autoCreateTopicEnable(boolean autoCreateTopicEnable) {
            this.autoCreateTopicEnable = autoCreateTopicEnable;
            return this;
        }

        public RocketMQBrokerContainer build() {
            return new RocketMQBrokerContainer(this);
        }
    }
}