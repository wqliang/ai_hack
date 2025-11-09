package ai.hack.rocketmq.model;

/**
 * Enumeration for message priority levels.
 * Influences processing order and queue placement.
 */
public enum MessagePriority {

    /**
     * Lowest priority - processed last.
     */
    LOW(1, "Low Priority"),

    /**
     * Normal priority - standard processing.
     */
    NORMAL(5, "Normal Priority"),

    /**
     * High priority - processed before normal messages.
     */
    HIGH(8, "High Priority"),

    /**
     * Critical priority - processed first in queue.
     */
    CRITICAL(10, "Critical Priority");

    private final int level;
    private final String description;

    MessagePriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * Gets the priority level (1-10, higher = more important).
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the priority description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this priority is higher than another.
     */
    public boolean isHigherThan(MessagePriority other) {
        return this.level > other.level;
    }

    /**
     * Gets the default priority for common use cases.
     */
    public static MessagePriority getDefault() {
        return NORMAL;
    }

    @Override
    public String toString() {
        return String.format("%s (Level: %d)", description, level);
    }
}