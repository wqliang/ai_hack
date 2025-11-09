package ai.hack.rocketmq.core;

import java.time.Instant;

/**
 * Interface for connections managed by the connection pool.
 * Represents a pooled RocketMQ producer or consumer connection.
 */
public interface PooledConnection {

    /**
     * Gets the unique identifier for this connection.
     */
    String getId();

    /**
     * Checks if the connection is healthy and usable.
     *
     * @return true if the connection is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Closes the connection and releases any resources.
     */
    void close();

    /**
     * Checks if this connection is currently borrowed (in use).
     *
     * @return true if borrowed, false otherwise
     */
    boolean isBorrowed();

    /**
     * Called when the connection is borrowed from the pool.
     */
    void borrowed();

    /**
     * Called when the connection is returned to the pool.
     */
    void returned();

    /**
     * Gets the time when this connection was created.
     *
     * @return creation time
     */
    Instant getCreated();

    /**
     * Gets the time when this connection was last used.
     *
     * @return last used time
     */
    Instant getLastUsed();
}