package com.paytech.foundation.web.error;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

/**
 * MVC 异常经安全分类后的内部 Problem 描述。
 *
 * @param status
 *            标准 HTTP 状态
 * @param errorCode
 *            稳定错误码
 * @param detail
 *            可安全外显的说明
 * @param errors
 *            仅请求 Body 语义校验可用的字段错误
 * @param headers
 *            明确允许保留的标准响应 Header
 * @author Codex
 */
public record MappedProblem(
    HttpStatus status,
    CodeMessageEnum errorCode,
    String detail,
    List<ProblemFieldError> errors,
    HttpHeaders headers
) {

    public MappedProblem {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        detail = detail == null || detail.isBlank() ? errorCode.getMessage() : detail;
        errors = errors == null ? List.of() : List.copyOf(errors);
        headers = headers == null || headers.isEmpty()
            ? HttpHeaders.EMPTY
            : HttpHeaders.readOnlyHttpHeaders(HttpHeaders.copyOf(headers));
    }
}
