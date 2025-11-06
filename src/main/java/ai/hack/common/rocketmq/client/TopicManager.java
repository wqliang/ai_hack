package ai.hack.common.rocketmq.client;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.command.CommandUtil;
import org.apache.rocketmq.common.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * RocketMQ Topic 管理工具类
 * 用于创建、删除、查询 Topic
 *
 * 使用示例:
 * <pre>
 * TopicManager manager = new TopicManager("127.0.0.1:9876");
 * manager.start();
 *
 * // 创建 Topic
 * manager.createTopic("my-topic", 4, 2);
 *
 * // 查询 Topic
 * boolean exists = manager.topicExists("my-topic");
 *
 * // 删除 Topic
 * manager.deleteTopic("my-topic");
 *
 * manager.shutdown();
 * </pre>
 */
public class TopicManager {

    private static final Logger log = LoggerFactory.getLogger(TopicManager.class);

    private final String namesrvAddr;
    private final DefaultMQAdminExt adminExt;
    private volatile boolean started = false;

    /**
     * 构造函数
     *
     * 实现思路：
     * 1. 保存 NameServer 地址
     * 2. 创建 RocketMQ 管理扩展客户端
     * 3. 配置客户端连接到指定的 NameServer
     * 4. 设置唯一的实例名称避免冲突
     *
     * @param namesrvAddr NameServer 地址（例如：127.0.0.1:9876）
     */
    public TopicManager(String namesrvAddr) {
        // 步骤1: 保存 NameServer 地址以便后续使用
        this.namesrvAddr = namesrvAddr;

        // 步骤2: 创建 DefaultMQAdminExt 实例，这是 RocketMQ 提供的管理工具
        this.adminExt = new DefaultMQAdminExt();

        // 步骤3: 设置 NameServer 地址，所有管理操作都通过 NameServer 协调
        this.adminExt.setNamesrvAddr(namesrvAddr);

        // 步骤4: 设置唯一实例名，使用时间戳避免多个 TopicManager 实例冲突
        this.adminExt.setInstanceName("TopicManager-" + System.currentTimeMillis());
    }

    /**
     * 启动管理客户端
     *
     * 实现思路：
     * 1. 检查是否已启动，避免重复启动
     * 2. 启动 RocketMQ 管理客户端
     * 3. 标记为已启动状态
     * 4. 记录启动日志
     *
     * @throws MQClientException 启动失败时抛出
     */
    public void start() throws MQClientException {
        // 步骤1: 检查启动状态，避免重复启动
        if (!started) {
            // 步骤2: 启动管理客户端，建立与 NameServer 的连接
            adminExt.start();

            // 步骤3: 标记为已启动
            started = true;

            // 步骤4: 记录启动成功日志
            log.info("TopicManager started, connected to NameServer: {}", namesrvAddr);
        }
    }

    /**
     * 关闭管理客户端
     *
     * 实现思路：
     * 1. 检查是否已启动
     * 2. 关闭管理客户端，释放资源
     * 3. 标记为未启动状态
     * 4. 记录关闭日志
     */
    public void shutdown() {
        // 步骤1: 检查是否已启动，避免重复关闭
        if (started) {
            // 步骤2: 关闭管理客户端，断开连接并释放资源
            adminExt.shutdown();

            // 步骤3: 标记为未启动
            started = false;

            // 步骤4: 记录关闭日志
            log.info("TopicManager shutdown");
        }
    }

    /**
     * 创建 Topic（简化版本，使用默认队列数）
     *
     * @param topicName Topic 名称
     * @param readQueueNums 读队列数量
     * @param writeQueueNums 写队列数量
     * @throws Exception 创建失败时抛出
     */
    public void createTopic(String topicName, int readQueueNums, int writeQueueNums) throws Exception {
        createTopic(topicName, readQueueNums, writeQueueNums, null);
    }

    /**
     * 创建 Topic（使用默认配置：4个读队列，4个写队列）
     *
     * @param topicName Topic 名称
     * @throws Exception 创建失败时抛出
     */
    public void createTopic(String topicName) throws Exception {
        createTopic(topicName, 4, 4, null);
    }

    /**
     * 创建 Topic（完整版本）
     *
     * 实现思路：
     * 1. 确保客户端已启动
     * 2. 创建 Topic 配置对象
     * 3. 设置 Topic 的各项参数（名称、队列数、权限等）
     * 4. 获取目标 Broker 地址列表
     * 5. 向每个 Broker 发送创建 Topic 的请求
     * 6. 等待所有 Broker 创建完成
     *
     * @param topicName Topic 名称
     * @param readQueueNums 读队列数量（消费者可以从多少个队列读取）
     * @param writeQueueNums 写队列数量（生产者可以向多少个队列写入）
     * @param clusterName 集群名称（可选，null 表示在所有集群创建）
     * @throws Exception 创建失败时抛出
     */
    public void createTopic(String topicName, int readQueueNums, int writeQueueNums, String clusterName) throws Exception {
        // 步骤1: 确保管理客户端已经启动
        ensureStarted();

        // 记录创建 Topic 的操作日志
        log.info("Creating topic: {}, readQueueNums: {}, writeQueueNums: {}, cluster: {}",
                topicName, readQueueNums, writeQueueNums, clusterName);

        // 步骤2: 创建 Topic 配置对象
        TopicConfig topicConfig = new TopicConfig();

        // 步骤3: 设置 Topic 的各项参数
        topicConfig.setTopicName(topicName);          // Topic 名称
        topicConfig.setReadQueueNums(readQueueNums);   // 读队列数量
        topicConfig.setWriteQueueNums(writeQueueNums); // 写队列数量
        topicConfig.setPerm(6);                        // 权限：6 = 读写权限 (2=写,4=读,6=读写)

        // 步骤4: 获取目标 Broker 地址列表
        Set<String> brokerAddrs;
        if (clusterName != null && !clusterName.isEmpty()) {
            // 如果指定了集群名，只获取该集群的 Master Broker 地址
            brokerAddrs = CommandUtil.fetchMasterAddrByClusterName(adminExt, clusterName);
        } else {
            // 否则获取所有 Broker 地址（Master 和 Slave）
            brokerAddrs = getAllBrokers(adminExt);
        }

        // 验证是否找到可用的 Broker
        if (brokerAddrs == null || brokerAddrs.isEmpty()) {
            throw new IllegalStateException("No broker found in cluster: " + clusterName);
        }

        // 步骤5: 向每个 Broker 发送创建 Topic 的请求
        for (String brokerAddr : brokerAddrs) {
            try {
                // 调用 RocketMQ API 在指定 Broker 上创建或更新 Topic 配置
                adminExt.createAndUpdateTopicConfig(brokerAddr, topicConfig);
                log.info("Topic '{}' created on broker: {}", topicName, brokerAddr);
            } catch (Exception e) {
                log.error("Failed to create topic '{}' on broker: {}", topicName, brokerAddr, e);
                throw e;
            }
        }

        // 步骤6: 记录创建成功日志
        log.info("Topic '{}' created successfully on {} broker(s)", topicName, brokerAddrs.size());
    }

    /**
     * 删除 Topic（简化版本）
     *
     * @param topicName Topic 名称
     * @throws Exception 删除失败时抛出
     */
    public void deleteTopic(String topicName) throws Exception {
        deleteTopic(topicName, null);
    }

    /**
     * 删除 Topic
     *
     * 实现思路：
     * 1. 确保客户端已启动
     * 2. 获取该 Topic 所在的所有 Broker 地址
     * 3. 从每个 Broker 删除 Topic 配置
     * 4. 从 NameServer 删除 Topic 的路由信息
     * 5. 等待删除操作完成
     *
     * @param topicName Topic 名称
     * @param clusterName 集群名称（可选，null 表示在所有集群删除）
     * @throws Exception 删除失败时抛出
     */
    public void deleteTopic(String topicName, String clusterName) throws Exception {
        // 步骤1: 确保管理客户端已启动
        ensureStarted();

        log.info("Deleting topic: {}, cluster: {}", topicName, clusterName);

        // 步骤2: 获取 Broker 地址列表
        Set<String> brokerAddrs;
        if (clusterName != null && !clusterName.isEmpty()) {
            // 如果指定了集群，获取该集群的 Master Broker
            brokerAddrs = CommandUtil.fetchMasterAddrByClusterName(adminExt, clusterName);
        } else {
            // 否则获取所有 Broker 地址
            brokerAddrs = getAllBrokers(adminExt);
        }

        // 检查是否找到 Broker
        if (brokerAddrs == null || brokerAddrs.isEmpty()) {
            log.warn("No broker found for topic: {}", topicName);
            return;
        }

        // 步骤3: 从每个 Broker 删除 Topic
        for (String brokerAddr : brokerAddrs) {
            try {
                adminExt.deleteTopicInBroker(Set.of(brokerAddr), topicName);
                log.info("Topic '{}' deleted from broker: {}", topicName, brokerAddr);
            } catch (Exception e) {
                log.error("Failed to delete topic '{}' from broker: {}", topicName, brokerAddr, e);
            }
        }

        // 步骤4: 从 NameServer 删除路由信息
        try {
            adminExt.deleteTopicInNameServer(Set.of(namesrvAddr), topicName);
            log.info("Topic '{}' deleted from NameServer", topicName);
        } catch (Exception e) {
            log.error("Failed to delete topic '{}' from NameServer", topicName, e);
        }

        log.info("Topic '{}' deleted successfully", topicName);
    }

    /**
     * 检查 Topic 是否存在
     *
     * 实现思路：
     * 1. 确保客户端已启动
     * 2. 获取任意一个 Broker 地址
     * 3. 查询该 Broker 上的 Topic 配置
     * 4. 如果配置不为 null，说明 Topic 存在
     * 5. 捕获所有异常并返回 false（Topic 不存在或查询失败）
     *
     * @param topicName Topic 名称
     * @return true 如果存在
     */
    public boolean topicExists(String topicName) {
        try {
            // 步骤1: 确保客户端已启动
            ensureStarted();

            // 步骤2: 获取所有 Broker 地址
            Set<String> brokerAddrs = getAllBrokers(adminExt);

            // 步骤3: 如果有 Broker，查询第一个 Broker 上的 Topic 配置
            if (brokerAddrs != null && !brokerAddrs.isEmpty()) {
                String brokerAddr = brokerAddrs.iterator().next();

                // 步骤4: 查询 Topic 配置
                TopicConfig topicConfig = adminExt.examineTopicConfig(brokerAddr, topicName);

                // 步骤5: 如果配置不为 null，说明 Topic 存在
                return topicConfig != null;
            }
            return false;
        } catch (Exception e) {
            // 步骤6: 捕获所有异常，返回 false
            return false;
        }
    }

    /**
     * 检查 Topic 是否存在（按集群）
     *
     * @param clusterName 集群名称
     * @param topicName Topic 名称
     * @return true 如果存在
     */
    public boolean topicExists(String clusterName, String topicName) {
        // 直接调用无集群名的版本，因为 Topic 是全局概念
        return topicExists(topicName);
    }

    /**
     * 获取 Topic 配置
     *
     * 实现思路：
     * 1. 确保客户端已启动
     * 2. 获取任意一个 Broker 地址
     * 3. 从该 Broker 获取 Topic 的详细配置
     * 4. 返回配置对象
     *
     * @param topicName Topic 名称
     * @return Topic 配置
     * @throws Exception 获取失败时抛出
     */
    public TopicConfig getTopicConfig(String topicName) throws Exception {
        // 步骤1: 确保客户端已启动
        ensureStarted();

        // 步骤2: 获取所有 Broker 地址
        Set<String> brokerAddrs = getAllBrokers(adminExt);

        // 步骤3: 从第一个 Broker 获取配置
        if (brokerAddrs != null && !brokerAddrs.isEmpty()) {
            String brokerAddr = brokerAddrs.iterator().next();

            // 步骤4: 查询并返回 Topic 配置
            return adminExt.examineTopicConfig(brokerAddr, topicName);
        }

        // 如果没有可用的 Broker，抛出异常
        throw new IllegalStateException("No broker available");
    }

    /**
     * 列出所有 Topic
     *
     * 实现思路：
     * 1. 确保客户端已启动
     * 2. 通过 RocketMQ API 获取所有 Topic 列表
     * 3. 返回 Topic 名称集合
     *
     * @return Topic 名称集合
     * @throws Exception 获取失败时抛出
     */
    public Set<String> listTopics() throws Exception {
        // 步骤1: 确保客户端已启动
        ensureStarted();

        // 步骤2-3: 获取并返回所有 Topic 列表
        return adminExt.fetchAllTopicList().getTopicList();
    }

    /**
     * 确保管理客户端已启动
     *
     * 实现思路：
     * 1. 检查启动状态
     * 2. 如果未启动，抛出异常提示用户先调用 start()
     */
    private void ensureStarted() {
        if (!started) {
            throw new IllegalStateException("TopicManager is not started. Please call start() first.");
        }
    }

    /**
     * 检查管理客户端是否已启动
     *
     * @return true 如果已启动
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 获取 NameServer 地址
     *
     * @return NameServer 地址
     */
    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    /**
     * 获取所有 Broker 地址
     * @param adminExt
     * @return
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     * @throws RemotingTimeoutException
     * @throws MQBrokerException
     * @throws InterruptedException
     */
    private Set<String> getAllBrokers(final MQAdminExt adminExt) throws RemotingSendRequestException, RemotingConnectException, RemotingTimeoutException, MQBrokerException, InterruptedException {
        Set<String> brokerAddrs = new HashSet<>();
        ClusterInfo clusterInfoSerializeWrapper = adminExt.examineBrokerClusterInfo();
        for (String clusterName1 : clusterInfoSerializeWrapper.getClusterAddrTable().keySet()) {
            brokerAddrs.addAll(CommandUtil.fetchMasterAndSlaveAddrByClusterName(adminExt, clusterName1));
        }
        return brokerAddrs;
    }
}