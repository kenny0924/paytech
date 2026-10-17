package com.paytech.foundation.common.error;

import java.io.Serial;

/**
 * 请求并发冲突的明确失败异常，使用公共 CONCURRENT_REQUEST 编码和 HTTP 409。
 *
 * <p>消息用于说明安全的冲突上下文；该异常不执行重试，也不表示未知事务结果。
 *
 * @author Codex
 */
public class ConcurrentRequestException extends FailException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentRequestException() {
        this(null);
    }

    public ConcurrentRequestException(String message) {
        super(CommonErrorCode.CONCURRENT_REQUEST, message);
    }

    public ConcurrentRequestException(String message, Throwable cause) {
        super(CommonErrorCode.CONCURRENT_REQUEST, message, cause);
    }
}
