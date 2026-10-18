package com.paytech.foundation.httpproblem.api;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

/** 调用方按本地 Body 合同完成投影的字段错误，由统一 MVC Writer 生成 422。 */
public final class RequestBodyValidationException extends FailException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final List<ProblemFieldError> errors;

    /** 字段及文案必须由本地边界确认可外显；空集合只返回公共校验失败。 */
    public RequestBodyValidationException(List<ProblemFieldError> errors, Throwable cause) {
        super(CommonErrorCode.VALIDATION_ERROR, null, cause);
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
