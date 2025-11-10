package ai.hack.rocketmq;

/**
 * Client operational states.
 * Tracks the lifecycle state of the RocketMQ async client.
 */
public enum ClientState {

    /**
     * Client has been created but not yet initialized.
     */
    CREATED("Client created, not initialized"),

    /**
     * Client is currently initializing components.
     */
    INITIALIZING("Client is initializing"),

    /**
     * Client is connecting to RocketMQ brokers.
     */
    CONNECTING("Client is connecting to brokers"),

    /**
     * Client is connected and operational.
     */
    READY("Client is ready for operations"),

    /**
     * Client is shutting down gracefully.
     */
    SHUTTING_DOWN("Client is shutting down"),

    /**
     * Client has been fully shut down.
     */
    SHUTDOWN("Client is shut down"),

    /**
     * Client is in an error state.
     */
    ERROR("Client encountered an error");

    private final String description;

    ClientState(String description) {
        this.description = description;
    }

    /**
     * Gets the state description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a terminal state.
     */
    public boolean isTerminal() {
        return this == SHUTDOWN || this == ERROR;
    }

    /**
     * Checks if the client can perform operations in this state.
     */
    public boolean canOperate() {
        return this == READY;
    }

    /**
     * Checks if the client is transitioning.
     */
    public boolean isTransitioning() {
        return this == INITIALIZING || this == CONNECTING || this == SHUTTING_DOWN;
    }

    @Override
    public String toString() {
        return description;
    }
}