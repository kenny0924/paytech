package com.paytech.foundation.messaging.core.codec;

/**
 * 收到的消息不是合法的信封 Schema v1；消息直接进入 DLQ，不重试。
 *
 * @author Claude
 */
public final class InvalidDomainEventException extends RuntimeException {

    /**
     * 创建不含 Payload 的安全说明。
     *
     * @param message 安全说明
     * @param cause 原始异常，可为 null
     */
    public InvalidDomainEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
