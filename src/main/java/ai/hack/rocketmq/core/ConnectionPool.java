package ai.hack.rocketmq.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance connection pool for RocketMQ client connections.
 * Provides thread-safe connection management with health checking and circuit breaking.
 */
public class ConnectionPool implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);

    private final int maxConnections;
    private final Duration maxIdleTime;
    private final Duration healthCheckInterval;
    private final BlockingQueue<PooledConnection> availableConnections;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnectionsCreated = new AtomicInteger(0);

    private final ReentrantReadWriteLock poolLock = new ReentrantReadWriteLock();
    private volatile boolean shutdown = false;

    private ScheduledExecutorService maintenanceExecutor;
    private ScheduledFuture<?> healthCheckTask;

    // Circuit breaker state
    private volatile int consecutiveFailures = 0;
    private volatile boolean circuitOpen = false;
    private final int circuitBreakerThreshold;
    private final Duration circuitBreakerTimeout;
    private Instant circuitOpenTime;

    public ConnectionPool(int maxConnections, Duration maxIdleTime, Duration healthCheckInterval) {
        this.maxConnections = Math.max(1, maxConnections);
        this.maxIdleTime = maxIdleTime;
        this.healthCheckInterval = healthCheckInterval;
        this.availableConnections = new LinkedBlockingQueue<>(this.maxConnections);
        this.circuitBreakerThreshold = 10; // Open circuit after 10 consecutive failures
        this.circuitBreakerTimeout = Duration.ofSeconds(30); // Open for 30 seconds
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        startMaintenanceTasks();
        logger.info("ConnectionPool initialized: maxConnections={}, maxIdleTime={}, healthCheckInterval={}",
                   maxConnections, maxIdleTime, healthCheckInterval);
    }

    @Override
    public void destroy() throws Exception {
        logger.info("Shutting down ConnectionPool");
        shutdown = true;

        if (healthCheckTask != null) {
            healthCheckTask.cancel(true);
        }

        if (maintenanceExecutor != null) {
            maintenanceExecutor.shutdown();
            try {
                if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    maintenanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                maintenanceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close all connections
        poolLock.writeLock().lock();
        try {
            PooledConnection connection;
            while ((connection = availableConnections.poll()) != null) {
                closeConnection(connection);
            }
        } finally {
            poolLock.writeLock().unlock();
        }

        logger.info("ConnectionPool shutdown complete. Total connections created: {}", totalConnectionsCreated.get());
    }

    /**
     * Acquires a connection from the pool.
     *
     * @return a PooledConnection
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public PooledConnection acquireConnection() throws InterruptedException {
        if (shutdown) {
            throw new IllegalStateException("ConnectionPool is shutdown");
        }

        // Check circuit breaker
        if (isCircuitOpen()) {
            throw new ConnectionPoolException("Circuit breaker is open due to recent failures", null);
        }

        // Try to get from pool first
        PooledConnection connection = availableConnections.poll();
        if (connection != null) {
            // Check if connection is still healthy
            if (connection.isHealthy()) {
                activeConnections.incrementAndGet();
                connection.borrowed();
                logger.debug("Acquired reusable connection: {}", connection.getId());
                return connection;
            } else {
                closeConnection(connection);
                totalConnectionsCreated.decrementAndGet();
            }
        }

        // Create new connection if we haven't reached max
        int currentTotal = totalConnectionsCreated.get();
        int currentActive = activeConnections.get();

        if (currentTotal < maxConnections && currentActive < maxConnections) {
            try {
                PooledConnection newConnection = createNewConnection();
                activeConnections.incrementAndGet();
                totalConnectionsCreated.incrementAndGet();

                connection = availableConnections.offer(newConnection) ? availableConnections.poll() : newConnection;
                connection.borrowed();

                logger.debug("Created and acquired new connection: {}", connection.getId());
                return connection;
            } catch (Exception e) {
                handleConnectionFailure();
                throw new ConnectionPoolException("Failed to create new connection", e);
            }
        }

        // Pool is exhausted, wait for a connection to become available
        connection = availableConnections.take(); // This will block
        if (connection.isHealthy()) {
            activeConnections.incrementAndGet();
            connection.borrowed();
            logger.debug("Acquired connection from blocking queue: {}", connection.getId());
            return connection;
        } else {
            closeConnection(connection);
            totalConnectionsCreated.decrementAndGet();
            throw new ConnectionPoolException("Unhealthy connection returned from pool", null);
        }
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection the connection to return
     * @throws IllegalArgumentException if the connection is null or invalid
     */
    public void returnConnection(PooledConnection connection) {
        if (connection == null || shutdown) {
            return;
        }

        connection.returned();

        if (!connection.isHealthy()) {
            logger.debug("Discarding unhealthy connection: {}", connection.getId());
            closeConnection(connection);
            activeConnections.decrementAndGet();
            totalConnectionsCreated.decrementAndGet();
            return;
        }

        // Check if we're over capacity
        if (totalConnectionsCreated.get() > maxConnections) {
            logger.debug("Pool over capacity, discarding connection: {}", connection.getId());
            closeConnection(connection);
            activeConnections.decrementAndGet();
            totalConnectionsCreated.decrementAndGet();
            return;
        }

        try {
            boolean offered = availableConnections.offer(connection);
            if (offered) {
                activeConnections.decrementAndGet();
                logger.debug("Returned connection to pool: {}", connection.getId());
            } else {
                // Should not happen if pool is sized correctly
                closeConnection(connection);
                activeConnections.decrementAndGet();
                logger.warn("Pool full, discarding connection: {}", connection.getId());
            }
        } catch (Exception e) {
            logger.error("Error returning connection to pool: {}", connection.getId(), e);
            closeConnection(connection);
            activeConnections.decrementAndGet();
        }
    }

    /**
     * Gets snapshot statistics about the connection pool.
     */
    public ConnectionPoolStats getStats() {
        poolLock.readLock().lock();
        try {
            return new ConnectionPoolStats(
                    maxConnections,
                    activeConnections.get(),
                    availableConnections.size(),
                    totalConnectionsCreated.get(),
                    consecutiveFailures,
                    circuitOpen,
                    circuitOpenTime,
                    !shutdown
            );
        } finally {
            poolLock.readLock().unlock();
        }
    }

    /**
     * Performs health check on all connections in the pool.
     */
    public void performHealthCheck() {
        if (shutdown) {
            return;
        }

        List<PooledConnection> allConnections = new ArrayList<>();
        availableConnections.drainTo(allConnections);

        boolean foundUnhealthy = false;
        List<PooledConnection> healthyConnections = new ArrayList<>();

        for (PooledConnection connection : allConnections) {
            if (connection.isHealthy()) {
                healthyConnections.add(connection);
            } else {
                foundUnhealthy = true;
                logger.debug("Found unhealthy connection: {}", connection.getId());
                closeConnection(connection);
                totalConnectionsCreated.decrementAndGet();
            }
        }

        // Return healthy connections back to pool
        for (PooledConnection connection : healthyConnections) {
            availableConnections.offer(connection);
        }

        if (foundUnhealthy) {
            logger.info("Health check completed - removed {} unhealthy connections",
                       allConnections.size() - healthyConnections.size());
        }
    }

    /**
     * Resets the circuit breaker state.
     */
    public void resetCircuitBreaker() {
        consecutiveFailures.set(0);
        circuitOpen = false;
        circuitOpenTime = null;
        logger.info("Circuit breaker reset");
    }

    private PooledConnection createNewConnection() throws Exception {
        logger.debug("Creating new RocketMQ connection for pool");

        // This is a placeholder for actual RocketMQ connection creation
        // In a real implementation, this would create actual RocketMQ producer/consumer connections
        String connectionId = "conn-" + System.currentTimeMillis() + "-" + totalConnectionsCreated.incrementAndGet();

        // Mock connection for demonstration purposes
        return new PooledConnection(connectionId) {
            private volatile boolean healthy = true;
            private volatile boolean borrowed = false;
            private Instant lastUsed = Instant.now();
            private Instant created = Instant.now();

            @Override
            public String getId() {
                return connectionId;
            }

            @Override
            public boolean isHealthy() {
                // Simple health check - in real implementation this would ping the broker
                if (!healthy || borrowed) {
                    return healthy;
                }

                // Check idle timeout
                return Instant.now().minus(maxIdleTime).isBefore(lastUsed);
            }

            @Override
            public void close() {
                healthy = false;
                logger.debug("Closing connection: {}", getId());
            }

            @Override
            public boolean isBorrowed() {
                return borrowed;
            }

            @Override
            public void borrowed() {
                this.borrowed = true;
                this.lastUsed = Instant.now();
            }

            @Override
            public void returned() {
                this.borrowed = false;
                this.lastUsed = Instant.now();
            }

            @Override
            public Instant getCreated() {
                return created;
            }

            @Override
            public Instant getLastUsed() {
                return lastUsed;
            }
        };
    }

    private void closeConnection(PooledConnection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.error("Error closing connection: {}", connection.getId(), e);
            }
        }
    }

    private void startMaintenanceTasks() {
        maintenanceExecutor = Executors.newScheduledThreadPool(1, new PoolThreadFactory());
        healthCheckTask = maintenanceExecutor.scheduleAtFixedRate(
                this::performHealthCheck,
                healthCheckInterval.toMillis(),
                healthCheckInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        // Also schedule circuit breaker check periodically
        maintenanceExecutor.scheduleAtFixedRate(
                this::checkCircuitBreaker,
                circuitBreakerTimeout.toMillis(),
                circuitBreakerTimeout.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private void handleConnectionFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        logger.warn("Connection failure occurred. Total failures: {}", failures);

        if (failures >= circuitBreakerThreshold && !circuitOpen) {
            openCircuit();
        }
    }

    private void openCircuit() {
        circuitOpen = true;
        circuitOpenTime = Instant.now();

        logger.error("Circuit breaker opened due to {} consecutive failures. Will remain open for {} seconds",
                     consecutiveFailures.get(), circuitBreakerTimeout.toSeconds());
    }

    private void checkCircuitBreaker() {
        if (circuitOpen && circuitOpenTime != null) {
            Duration timeOpen = Duration.between(circuitOpenTime, Instant.now());
            if (timeOpen.compareTo(circuitBreakerTimeout) >= 0) {
                resetCircuitBreaker();
            }
        }
    }

    private boolean isCircuitOpen() {
        if (!circuitOpen) {
            return false;
        }

        if (circuitOpenTime != null) {
            Duration timeOpen = Duration.between(circuitOpenTime, Instant.now());
            if (timeOpen.compareTo(circuitBreakerTimeout) >= 0) {
                resetCircuitBreaker();
                return false;
            }
        }

        return true;
    }

    /**
     * Thread factory for pool maintenance threads.
     */
    private static class PoolThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rocketmq-pool-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Exception thrown for connection pool errors.
     */
    public static class ConnectionPoolException extends RuntimeException {
        public ConnectionPoolException(String message) {
            super(message);
        }

        public ConnectionPoolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}