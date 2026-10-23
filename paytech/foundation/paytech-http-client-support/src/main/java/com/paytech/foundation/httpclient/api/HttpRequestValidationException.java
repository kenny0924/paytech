package com.paytech.foundation.httpclient.api;

import java.io.Serial;
import java.util.List;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

/**
 * 上游已经明确拒绝请求的合规 422；字段路径与字段文案仍为远端数据，消费方必须按本地 Body 投影。
 * 本异常的 message 恒为公共安全消息，不保留远端 detail；远端字段文案只作为待消费方按自己登记的安全文案逐字核对的输入，
 * 未登记的文案不得外显。原始响应只作为诊断 Cause。
 */
public final class HttpRequestValidationException extends FailException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<String> fieldPaths;
    private final List<ProblemFieldError> remoteFieldErrors;

    /** 保存不可变的远端字段定位，使用公共安全消息。 */
    public HttpRequestValidationException(List<String> fieldPaths, Throwable cause) {
        this(fieldPaths, List.of(), cause);
    }

    /** 同时保存已通过 Problem 校验的远端字段错误（路径与文案），供消费方按登记文案筛选。 */
    public HttpRequestValidationException(List<String> fieldPaths, List<ProblemFieldError> remoteFieldErrors,
        Throwable cause) {
        super(CommonErrorCode.VALIDATION_ERROR, null, cause);
        this.fieldPaths = List.copyOf(fieldPaths);
        this.remoteFieldErrors = List.copyOf(remoteFieldErrors);
    }

    /** 返回尚需消费方按自己的请求合同筛选的路径。 */
    public List<String> fieldPaths() {
        return fieldPaths;
    }

    /** 返回远端字段错误；文案未经本地登记前不可外显。 */
    public List<ProblemFieldError> remoteFieldErrors() {
        return remoteFieldErrors;
    }
}
