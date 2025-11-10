package ai.hack.rocketmq.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages RocketMQ broker connections with TLS support and connection pooling.
 * Provides thread-safe connection management for both producers and consumers.
 */
public class ConnectionManager implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final int maxConnections;
    private final String namesrvAddr;
    private final boolean tlsEnabled;
    private final ConnectionPool connectionPool;

    private volatile boolean shutdown = false;

    public ConnectionManager(String namesrvAddr, int maxConnections, boolean tlsEnabled) {
        this.namesrvAddr = namesrvAddr;
        this.maxConnections = maxConnections;
        this.tlsEnabled = tlsEnabled;
        this.connectionPool = new ConnectionPool(
                maxConnections,
                Duration.ofMinutes(5),  // max idle time
                Duration.ofSeconds(30) // health check interval
        );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing RocketMQ connection manager - NameServer: {}, TLS: {}, Max connections: {}",
                   namesrvAddr, tlsEnabled, maxConnections);
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down RocketMQ connection manager");
        shutdown = true;

        if (connectionPool != null) {
            connectionPool.destroy();
        }

        logger.info("RocketMQ connection manager shutdown complete - final active: {}",
                   activeConnections.get());
    }

    /**
     * Acquires a connection from the pool.
     *
     * @return PooledConnection that must be returned via releaseConnection
     * @throws IllegalStateException if manager is shutdown or circuit is open
     */
    public PooledConnection acquireConnection() {
        try {
            PooledConnection connection = connectionPool.acquireConnection();
            logger.debug("Pooled connection acquired: {}", connection.getId());
            return connection;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while acquiring connection", e);
        }
    }

    /**
     * Releases a connection back to the pool.
     */
    public void releaseConnection(PooledConnection connection) {
        if (connection != null) {
            connectionPool.returnConnection(connection);
            logger.debug("Pooled connection released: {}", connection.getId());
        }
    }

    /**
     * Performs health check on the connection pool.
     */
    public boolean isHealthy() {
        return !shutdown && activeConnections.get() <= maxConnections;
    }

    /**
     * Gets the current number of active connections.
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Gets the maximum allowed connections.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Gets the NameServer address.
     */
    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    /**
     * Checks if TLS is enabled.
     */
    public boolean isTlsEnabled() {
        return tlsEnabled;
    }
}