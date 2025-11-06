package ai.hack.common.rocketmq.namesrv;

import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RocketMQ NameServer 容器类
 */
public class RocketMQNameServerContainer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQNameServerContainer.class);

    private final NamesrvConfig namesrvConfig;
    private final NettyServerConfig nettyServerConfig;
    private NamesrvController namesrvController;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Object LOCK = new Object();

    private RocketMQNameServerContainer(Builder builder) {
        this.namesrvConfig = new NamesrvConfig();
        this.nettyServerConfig = new NettyServerConfig();

        this.nettyServerConfig.setListenPort(builder.listenPort);

        if (builder.rocketmqHome != null) {
            this.namesrvConfig.setRocketmqHome(builder.rocketmqHome);
            File homeDir = new File(builder.rocketmqHome);
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }
        }

        if (builder.kvConfigPath != null) {
            this.namesrvConfig.setKvConfigPath(builder.kvConfigPath);
        }

        log.info("RocketMQ NameServer container initialized with port: {}, home: {}",
                builder.listenPort, builder.rocketmqHome);
    }

    public void start() throws Exception {
        if (started.compareAndSet(false, true)) {
            log.info("Starting RocketMQ NameServer...");

            synchronized (LOCK) {
                if (started.compareAndSet(false, true)) {
                    this.namesrvController = new NamesrvController(namesrvConfig, nettyServerConfig);

                    boolean initResult = namesrvController.initialize();
                    if (!initResult) {
                        namesrvController.shutdown();
                        throw new RuntimeException("Failed to initialize RocketMQ NameServer");
                    }

                    namesrvController.start();
                }
            }

            log.info("RocketMQ NameServer started successfully on port: {}",
                    nettyServerConfig.getListenPort());
            log.info("NameServer address: {}:{}",
                    getAddress(), nettyServerConfig.getListenPort());
        } else {
            log.warn("RocketMQ NameServer is already started");
        }
    }

    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            log.info("Shutting down RocketMQ NameServer...");
            if (namesrvController != null) {
                namesrvController.shutdown();
            }
            log.info("RocketMQ NameServer stopped");
        } else {
            log.warn("RocketMQ NameServer is not running");
        }
    }

    public boolean isRunning() {
        return started.get();
    }

    public int getListenPort() {
        return nettyServerConfig.getListenPort();
    }

    public String getAddress() {
        return "127.0.0.1";
    }

    public String getFullAddress() {
        return getAddress() + ":" + getListenPort();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int listenPort = 9876;
        private String rocketmqHome = System.getProperty("user.home") + File.separator + "rocketmq-data";
        private String kvConfigPath = null;

        public Builder listenPort(int listenPort) {
            this.listenPort = listenPort;
            return this;
        }

        public Builder rocketmqHome(String rocketmqHome) {
            this.rocketmqHome = rocketmqHome;
            return this;
        }

        public Builder kvConfigPath(String kvConfigPath) {
            this.kvConfigPath = kvConfigPath;
            return this;
        }

        public RocketMQNameServerContainer build() {
            return new RocketMQNameServerContainer(this);
        }
    }
}