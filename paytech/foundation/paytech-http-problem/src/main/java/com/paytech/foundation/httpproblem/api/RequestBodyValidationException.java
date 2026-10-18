package com.paytech.foundation.httpproblem.api;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

/**
 * 调用方按本地 Body 合同完成投影的字段错误，由统一 MVC Writer 生成带 errors[] 的 Problem。
 *
 * <p>公共 VALIDATION_ERROR 固定 422；Owner 业务码（如入驻草稿字段不合规）由 Owner 状态解析决定 4xx，默认 422。
 * 字段路径使用外部 JSON 路径，文案由业务方给定、可安全外显，不含提交的原值。
 */
public final class RequestBodyValidationException extends FailException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<ProblemFieldError> errors;

    /** 字段及文案必须由本地边界确认可外显；空集合只返回公共校验失败。 */
    public RequestBodyValidationException(List<ProblemFieldError> errors, Throwable cause) {
        this(CommonErrorCode.VALIDATION_ERROR, errors, cause);
    }

    /**
     * Owner 业务码携带字段清单；顶层 detail 使用错误码默认消息。
     *
     * @param errorCode 稳定错误码，HTTP 状态由 Owner 状态解析决定
     * @param errors 已确认可外显的字段错误；空集合时 Problem 不带 errors
     * @param cause 诊断用原因，不外显
     */
    public RequestBodyValidationException(CodeMessageEnum errorCode, List<ProblemFieldError> errors, Throwable cause) {
        super(errorCode, null, cause);
        this.errors = List.copyOf(errors);
        for (ProblemFieldError error : this.errors) {
            Objects.requireNonNull(error.field(), "field must not be null");
            Objects.requireNonNull(error.message(), "message must not be null");
            if (error.field().isBlank() || error.message().isBlank()) {
                throw new IllegalArgumentException("字段及安全文案不能为空");
            }
        }
    }

    /** 返回本地已经批准的不可变字段错误。 */
    public List<ProblemFieldError> errors() {
        return errors;
    }
}
