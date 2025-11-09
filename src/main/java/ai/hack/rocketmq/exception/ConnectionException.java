package ai.hack.rocketmq.exception;

/**
 * Exception thrown when RocketMQ client cannot establish or maintain connection
 * with the broker or NameServer.
 */
public class ConnectionException extends RocketMQException {

    private final String brokerAddress;

    public ConnectionException(String brokerAddress, String message) {
        super(ErrorCode.CONNECTION_FAILED, message, brokerAddress);
        this.brokerAddress = brokerAddress;
    }

    public ConnectionException(String brokerAddress, String message, Throwable cause) {
        super(ErrorCode.CONNECTION_FAILED, message, cause, brokerAddress);
        this.brokerAddress = brokerAddress;
    }

    /**
     * Gets the broker address that failed to connect.
     */
    public String getBrokerAddress() {
        return brokerAddress;
    }
}