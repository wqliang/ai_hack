package ai.hack.rocketmq.security;

import ai.hack.rocketmq.exception.ErrorCode;
import ai.hack.rocketmq.exception.RocketMQException;

/**
 * Exception thrown for security-related failures in the RocketMQ client.
 */
public class RocketMQSecurityException extends RocketMQException {

    public RocketMQSecurityException(String message) {
        super(ErrorCode.SECURITY_ERROR, message);
    }

    public RocketMQSecurityException(String message, Throwable cause) {
        super(ErrorCode.SECURITY_ERROR, message, cause);
    }

    public RocketMQSecurityException(String message, String context) {
        super(ErrorCode.SECURITY_ERROR, message, context);
    }

    public RocketMQSecurityException(String message, Throwable cause, String context) {
        super(ErrorCode.SECURITY_ERROR, message, cause, context);
    }

    public RocketMQSecurityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RocketMQSecurityException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}