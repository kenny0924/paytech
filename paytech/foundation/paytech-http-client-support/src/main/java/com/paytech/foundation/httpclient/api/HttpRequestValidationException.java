package com.paytech.foundation.httpclient.api;

import java.io.Serial;
import java.util.List;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

/**
 * 上游已经明确拒绝请求的合规 422；字段路径仍为远端数据，消费方必须按本地 Body 投影。
 * 不保留远端 detail 或字段文案为可外显消息，原始响应只作为诊断 Cause。
 */
public final class HttpRequestValidationException extends FailException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<String> fieldPaths;

    /** 保存不可变的远端字段定位，使用公共安全消息。 */
    public HttpRequestValidationException(List<String> fieldPaths, Throwable cause) {
        super(CommonErrorCode.VALIDATION_ERROR, null, cause);
        this.fieldPaths = List.copyOf(fieldPaths);
    }

    /** 返回尚需消费方按自己的请求合同筛选的路径。 */
    public List<String> fieldPaths() {
        return fieldPaths;
    }
}
