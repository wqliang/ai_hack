package ai.hack.rocketmq.persistence;

import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.model.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * H2 database for storing message metadata and indexing.
 * Provides SQL capabilities for complex queries and indexing.
 */
public class H2MetadataStore implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(H2MetadataStore.class);

    private final String dbPath;
    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;

    public H2MetadataStore(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabase();
        logger.info("H2 metadata store initialized at: {}", dbPath);
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down H2 metadata store");
        if (dataSource != null) {
            dataSource.shutdown();
        }
        logger.info("H2 metadata store shutdown complete");
    }

    private void initializeDatabase() throws RocketMQException {
        try {
            dataSource = new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName(dbPath)
                    .addScript("schema-h2.sql")
                    .build();

            jdbcTemplate = new JdbcTemplate(dataSource);

            // Create tables
            createTables();

            logger.info("H2 database initialized successfully");

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to initialize H2 database", e, dbPath);
        }
    }

    private void createTables() {
        // Message metadata table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS message_metadata (
                message_id VARCHAR(64) PRIMARY KEY,
                topic VARCHAR(255) NOT NULL,
                callback_topic VARCHAR(255),
                status VARCHAR(20) NOT NULL,
                retry_count INTEGER DEFAULT 0,
                priority VARCHAR(20) DEFAULT 'NORMAL',
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                index created_at_idx (created_at),
                index topic_status_idx (topic, status),
                index status_idx (status)
            )
        """);

        // Message index for correlation
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS message_correlation (
                correlation_id VARCHAR(64) PRIMARY KEY,
                message_id VARCHAR(64) NOT NULL,
                response_topic VARCHAR(255),
                timeout_at TIMESTAMP NOT NULL,
                index timeout_idx (timeout_at),
                FOREIGN KEY (message_id) REFERENCES message_metadata(message_id)
            )
        """);

        // System metrics table
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS system_metrics (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                metric_name VARCHAR(100) NOT NULL,
                metric_value DOUBLE NOT NULL,
                recorded_at TIMESTAMP NOT NULL,
                index metric_name_idx (metric_name),
                index recorded_at_idx (recorded_at)
            )
        """);

        logger.info("Database tables created successfully");
    }

    /**
     * Stores message metadata.
     */
    public void storeMessageMetadata(String messageId, String topic, String callbackTopic,
                                    MessageStatus status, int retryCount, String priority) throws RocketMQException {
        try {
            String sql = """
                INSERT INTO message_metadata (message_id, topic, callback_topic, status, retry_count,
                                              priority, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    topic = VALUES(topic),
                    callback_topic = VALUES(callback_topic),
                    status = VALUES(status),
                    retry_count = VALUES(retry_count),
                    priority = VALUES(priority),
                    updated_at = VALUES(updated_at)
                """;

            Instant now = Instant.now();
            jdbcTemplate.update(sql, messageId, topic, callbackTopic, status.name(),
                    retryCount, priority, now, now);

            logger.debug("Message metadata stored: {}", messageId);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to store message metadata", e, messageId);
        }
    }

    /**
     * Updates message status.
     */
    public void updateMessageStatus(String messageId, MessageStatus status, int retryCount) throws RocketMQException {
        try {
            String sql = """
                UPDATE message_metadata
                SET status = ?, retry_count = ?, updated_at = ?
                WHERE message_id = ?
                """;

            jdbcTemplate.update(sql, status.name(), retryCount, Instant.now(), messageId);

            logger.debug("Message status updated: {} -> {}", messageId, status);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to update message status", e, messageId);
        }
    }

    /**
     * Gets message metadata by ID.
     */
    public Map<String, Object> getMessageMetadata(String messageId) throws RocketMQException {
        try {
            String sql = """
                SELECT message_id, topic, callback_topic, status, retry_count,
                       priority, created_at, updated_at
                FROM message_metadata
                WHERE message_id = ?
                """;

            return jdbcTemplate.queryForMap(sql, messageId);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve message metadata", e, messageId);
        }
    }

    /**
     * Gets messages by status.
     */
    public List<Map<String, Object>> getMessagesByStatus(MessageStatus status, int limit) throws RocketMQException {
        try {
            String sql = """
                SELECT message_id, topic, callback_topic, status, retry_count,
                       priority, created_at, updated_at
                FROM message_metadata
                WHERE status = ?
                ORDER BY created_at ASC
                LIMIT ?
                """;

            return jdbcTemplate.queryForList(sql, status.name(), limit);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve messages by status", e, status.name());
        }
    }

    /**
     * Stores request-response correlation.
     */
    public void storeCorrelation(String correlationId, String messageId, String responseTopic,
                                Instant timeoutAt) throws RocketMQException {
        try {
            String sql = """
                INSERT INTO message_correlation (correlation_id, message_id, response_topic, timeout_at)
                VALUES (?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql, correlationId, messageId, responseTopic, timeoutAt);

            logger.debug("Correlation stored: {} -> {}", correlationId, messageId);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to store correlation", e, correlationId);
        }
    }

    /**
     * Gets correlation and removes it.
     */
    public Map<String, Object> getAndRemoveCorrelation(String correlationId) throws RocketMQException {
        try {
            Map<String, Object> correlation = jdbcTemplate.queryForMap(
                    "SELECT * FROM message_correlation WHERE correlation_id = ?", correlationId);

            jdbcTemplate.update("DELETE FROM message_correlation WHERE correlation_id = ?", correlationId);

            return correlation;

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve correlation", e, correlationId);
        }
    }

    /**
     * Cleans up expired correlations.
     */
    public int cleanupExpiredCorrelations() throws RocketMQException {
        try {
            int count = jdbcTemplate.update(
                    "DELETE FROM message_correlation WHERE timeout_at < ?", Instant.now());

            if (count > 0) {
                logger.info("Cleaned up {} expired correlations", count);
            }

            return count;

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to cleanup expired correlations", e);
        }
    }

    /**
     * Stores system metrics.
     */
    public void storeMetric(String metricName, double metricValue) throws RocketMQException {
        try {
            String sql = """
                INSERT INTO system_metrics (metric_name, metric_value, recorded_at)
                VALUES (?, ?, ?)
                """;

            jdbcTemplate.update(sql, metricName, metricValue, Instant.now());

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to store metric", e, metricName);
        }
    }

    /**
     * Gets recent metrics for a specific metric name.
     */
    public List<Map<String, Object>> getRecentMetrics(String metricName, int limit) throws RocketMQException {
        try {
            String sql = """
                SELECT metric_name, metric_value, recorded_at
                FROM system_metrics
                WHERE metric_name = ?
                ORDER BY recorded_at DESC
                LIMIT ?
                """;

            return jdbcTemplate.queryForList(sql, metricName, limit);

        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve metrics", e, metricName);
        }
    }

    /**
     * Gets database health status.
     */
    public boolean isHealthy() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            logger.error("H2 database health check failed", e);
            return false;
        }
    }
}