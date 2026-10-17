package com.paytech.foundation.messaging.api;

import com.paytech.foundation.common.error.ErrorException;

/**
 * 领域事件未确认送达 Broker；结果未知或失败，调用方按补偿任务的可重试失败处理。
 *
 * @author Claude
 */
public final class DomainEventPublishException extends ErrorException {

    /**
     * 创建不含原因的发布失败。
     *
     * @param message 不含 Payload 与凭据的安全说明
     */
    public DomainEventPublishException(String message) {
        super(message);
    }

    /**
     * 创建保留原因的发布失败。
     *
     * @param message 不含 Payload 与凭据的安全说明
     * @param cause 原始异常
     */
    public DomainEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
