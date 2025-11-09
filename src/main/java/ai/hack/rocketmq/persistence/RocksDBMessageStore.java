package ai.hack.rocketmq.persistence;

import ai.hack.rocketmq.exception.RocketMQException;
import ai.hack.rocketmq.model.Message;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RocksDB-based message store for local persistence.
 * Provides high-performance message storage with write-ahead logging.
 */
public class RocksDBMessageStore implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(RocksDBMessageStore.class);

    private final String dbPath;
    private final Duration flushInterval;
    private final int writeBufferSize;
    private final long cacheSize;

    private RocksDB db;
    private WriteBatch writeBatch;
    private volatile boolean shutdown = false;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDBMessageStore(String dbPath, Duration flushInterval, int writeBufferSize, long cacheSize) {
        this.dbPath = dbPath;
        this.flushInterval = flushInterval;
        this.writeBufferSize = writeBufferSize;
        this.cacheSize = cacheSize;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabase();
        startFlushThread();
        logger.info("RocksDB message store initialized at: {}", dbPath);
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down RocksDB message store");
        shutdown = true;

        if (writeBatch != null) {
            writeBatch.close();
        }

        if (db != null) {
            db.close();
        }

        logger.info("RocksDB message store shutdown complete");
    }

    private void initializeDatabase() throws RocketMQException {
        try {
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            Options options = new Options()
                    .setCreateIfMissing(true)
                    .setWriteBufferSize(writeBufferSize)
                    .setMaxWriteBufferNumber(3)
                    .setMinWriteBufferNumberToMerge(1)
                    .setMaxBackgroundCompactions(4)
                    .setMaxBackgroundFlushes(2);

            // Configure cache
            Cache cache = new LRUCache(cacheSize);
            options.setRowCache(cache);

            db = RocksDB.open(options, dbPath);
            writeBatch = new WriteBatch();

            logger.info("RocksDB opened successfully with cache size: {} bytes", cacheSize);

        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to initialize RocksDB database", e, dbPath);
        }
    }

    private void startFlushThread() {
        Thread flushThread = new Thread(() -> {
            while (!shutdown) {
                try {
                    Thread.sleep(flushInterval.toMillis());
                    flushBatch();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error during flush operation", e);
                }
            }
        }, "rocksdb-flush-thread");

        flushThread.setDaemon(true);
        flushThread.start();
    }

    /**
     * Stores a message in RocksDB.
     */
    public void storeMessage(Message message) throws RocketMQException {
        try {
            String key = createMessageKey(message);
            byte[] value = serializeMessage(message);
            writeBatch.put(key.getBytes(StandardCharsets.UTF_8), value);
            logger.debug("Message queued for storage: {}", message.getMessageId());
        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to store message", e, message.getMessageId());
        }
    }

    /**
     * Retrieves a message from RocksDB.
     */
    public Message retrieveMessage(String messageId) throws RocketMQException {
        try {
            String key = "msg:" + messageId;
            byte[] value = db.get(key.getBytes(StandardCharsets.UTF_8));

            if (value == null) {
                return null;
            }

            return deserializeMessage(value);
        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve message", e, messageId);
        }
    }

    /**
     * Deletes a message from RocksDB.
     */
    public void deleteMessage(String messageId) throws RocketMQException {
        try {
            String key = "msg:" + messageId;
            writeBatch.delete(key.getBytes(StandardCharsets.UTF_8));
            logger.debug("Message queued for deletion: {}", messageId);
        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to delete message", e, messageId);
        }
    }

    /**
     * Gets messages for a specific topic.
     */
    public List<Message> getMessagesByTopic(String topic) throws RocketMQException {
        List<Message> messages = new ArrayList<>();
        String prefix = "topic:" + topic + ":";

        try (RocksIterator iterator = db.newIterator()) {
            iterator.seek(prefix.getBytes(StandardCharsets.UTF_8));

            while (iterator.isValid()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (!key.startsWith(prefix)) {
                    break;
                }

                Message message = deserializeMessage(iterator.value());
                if (message != null) {
                    messages.add(message);
                }

                iterator.next();
            }
        } catch (Exception e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to retrieve messages by topic", e, topic);
        }

        return messages;
    }

    /**
     * Flushes the write batch to disk.
     */
    public synchronized void flushBatch() throws RocketMQException {
        try {
            if (!writeBatch.isEmpty()) {
                db.write(new WriteOptions(), writeBatch);
                writeBatch.clear();
                logger.debug("Write batch flushed to disk");
            }
        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to flush write batch", e);
        }
    }

    /**
     * Gets database statistics.
     */
    public String getStatistics() throws RocketMQException {
        try {
            return db.getProperty("rocksdb.stats");
        } catch (RocksDBException e) {
            throw new RocketMQException(ai.hack.rocketmq.exception.ErrorCode.PERSISTENCE_ERROR,
                    "Failed to get database statistics", e);
        }
    }

    private String createMessageKey(Message message) {
        // Create multiple indexes for efficient lookup
        String messageIdKey = "msg:" + message.getMessageId();
        String topicKey = "topic:" + message.getTopic() + ":" + message.getMessageId();

        try {
            // Store with message ID as primary key
            writeBatch.put(messageIdKey.getBytes(StandardCharsets.UTF_8), serializeMessage(message));
            // Store topic index
            writeBatch.put(topicKey.getBytes(StandardCharsets.UTF_8), messageIdKey.getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to create message keys", e);
        }

        return messageIdKey;
    }

    private byte[] serializeMessage(Message message) {
        // Simplified serialization - in production, use a proper serialization framework
        StringBuilder sb = new StringBuilder();
        sb.append(message.getMessageId()).append("|");
        sb.append(message.getTopic()).append("|");
        sb.append(new String(message.getPayload(), StandardCharsets.UTF_8)).append("|");
        sb.append(message.getTimestamp().toString()).append("|");
        sb.append(message.getPriority() != null ? message.getPriority().name() : "NORMAL");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Message deserializeMessage(byte[] data) {
        // Simplified deserialization - in production, use proper deserialization
        String content = new String(data, StandardCharsets.UTF_8);
        String[] parts = content.split("\\|");

        if (parts.length < 4) {
            return null;
        }

        try {
            return Message.builder()
                    .messageId(parts[0])
                    .topic(parts[1])
                    .payload(parts[2].getBytes(StandardCharsets.UTF_8))
                    .timestamp(Instant.parse(parts[3]))
                    .priority(ai.hack.rocketmq.model.MessagePriority.valueOf(parts.length > 4 ? parts[4] : "NORMAL"))
                    .build();
        } catch (Exception e) {
            logger.error("Failed to deserialize message", e);
            return null;
        }
    }
}