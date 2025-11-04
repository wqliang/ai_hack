/**
 * Copyright (C) @2025 Webank Group Holding Limited
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package ai.hack.test;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.FlushDiskType;
import org.apache.rocketmq.store.config.MessageStoreConfig;

import java.io.File;

public class EmbeddedRocketMQBroker {
    private static final String ROCKETMQ_BROKER_HOME = "rocketmq.home.dir";

    public static void main(String[] args) {
        try {
            // 0. 设置 RocketMQ Home 环境变量 (如果未设置)
            // 很多配置依赖这个路径
            String rocketmqHome = System.getProperty(ROCKETMQ_BROKER_HOME);
            if (rocketmqHome == null) {
                // 设置一个临时目录
                rocketmqHome = System.getProperty("user.home") + File.separator + "rocketmq-home";
                System.setProperty("rocketmq.home.dir", rocketmqHome);
            }

            // 1. 启动 NameServer
            NamesrvController namesrvController = startNameServer();
            System.out.printf("NameServer Started, listening at %s%n", namesrvController.getNettyServerConfig().getListenPort());

            // 2. 启动 Broker
            BrokerController brokerController = startBroker(namesrvController.getNettyServerConfig().getListenPort());
            System.out.printf("Broker Started, BrokerName: %s, Addr: %s%n",
                    brokerController.getBrokerConfig().getBrokerName(),
                    brokerController.getBrokerAddr());

            // 3. 增加 JVM 关闭钩子，确保优雅停机
            addShutdownHook(brokerController, namesrvController);

            System.out.println("RocketMQ 最小化嵌入式启动成功.");
            System.out.println("按下 Ctrl+ 停止...");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 启动 NameServer
     */
    private static NamesrvController startNameServer() throws Exception {
        // 1.1 NameServer 配置
        final NamesrvConfig namesrvConfig = new NamesrvConfig();
        // 1.2 Netty Server 配置
        final NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setListenPort(9876); // 设置 NameServer 端口

        // 1.3 创建 Controller
        NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig);

        // 1.4 初始化并启动
        controller.initialize();
        controller.start();

        return controller;
    }

    /**
     * 启动 Broker (最小化资源配置)
     */
    private static BrokerController startBroker(int namesrvPort) throws Exception {
        // 设置 RocketMQ 版本, 避免 JNI 错误
        System.setProperty(ROCKETMQ_BROKER_HOME, System.getProperty(ROCKETMQ_BROKER_HOME));

        // 2.1 Broker 核心配置
        final BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setBrokerName("MinimalBroker");
        brokerConfig.setBrokerId(0L); // 0 表示 Master
        brokerConfig.setNamesrvAddr("127.0.0.1:" + namesrvPort);
        brokerConfig.setEnablePropertyFilter(true); // 开启属性过滤（如果需要）

        // 2.2 消息存储配置 (*** 资源最小化的关键 ***)
        final MessageStoreConfig storeConfig = new MessageStoreConfig();

        // 存储路径
        String storePath = System.getProperty(ROCKETMQ_BROKER_HOME) + File.separator + "store";
        storeConfig.setStorePathRootDir(storePath);

        // *** 关键配置：减小文件大小 ***
        // CommitLog 文件大小 (默认 1G)，这里改为 20MB
        storeConfig.setMappedFileSizeCommitLog(20 * 1024 * 1024);
        // ConsumeQueue 文件大小 (默认 5.7MB)，这里改为 1MB
        storeConfig.setMappedFileSizeConsumeQueue(1024 * 1024);

        // *** 关键配置：刷盘方式 ***
        // 异步刷盘 (ASYNC_FLUSH) 能极大降低 I/O 压力，节省资源。
        // SYNC_FLUSH (同步刷盘) 会更耗资源。
        storeConfig.setFlushDiskType(FlushDiskType.ASYNC_FLUSH);

        // *** 关键配置：消息保留时间 ***
        // 默认 72 小时，改为 4 小时，以便更快清理磁盘
        storeConfig.setFileReservedTime(4);
        // 凌晨2点删除过期文件
        storeConfig.setDeleteWhen("02");

        // 禁用 Dledger (HA)，单点模式最省资源
        storeConfig.setEnableDLegerCommitLog(false);

        // 2.3 Netty Server 配置 (Broker 端口)
        final NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setListenPort(10911); // Broker 监听端口

        // 2.4 Netty Client 配置 (Broker 连接 NameServer 用)
        final NettyClientConfig nettyClientConfig = new NettyClientConfig();

        // 2.5 创建 Controller
        BrokerController controller = new BrokerController(
                brokerConfig,
                nettyServerConfig,
                nettyClientConfig,
                storeConfig
        );

        // 2.6 初始化并启动
        controller.initialize();
        controller.start();

        return controller;
    }

    /**
     * 注册 JVM 关闭钩子
     */
    private static void addShutdownHook(BrokerController broker, NamesrvController namesrv) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down embedded RocketMQ...");
            if (broker != null) {
                broker.shutdown();
            }
            if (namesrv != null) {
                namesrv.shutdown();
            }
            System.out.println("Embedded RocketMQ shutdown complete.");
        }));
    }
}
