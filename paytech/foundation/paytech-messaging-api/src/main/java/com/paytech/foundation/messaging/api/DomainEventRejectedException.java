package com.paytech.foundation.messaging.api;

/**
 * Handler 明确拒绝一条事件：内容不合法或业务规则不允许，重投不会改变结果。
 * <li>监听器回滚本次消费登记并直接把消息拒绝进入 DLQ，不消耗重投次数；其他运行时异常仍按可重试处理。</li>
 * <li>消息只写安全上下文，不含 Payload 原文。</li>
 *
 * @author Claude
 */
public final class DomainEventRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建拒绝异常。
     *
     * @param message 安全说明
     * @param cause 业务原因
     */
    public DomainEventRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
