package com.paytech.foundation.web.error;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

/**
 * 使用可注入时钟创建 Code-MoR Problem Profile。
 *
 * @author Codex
 */
public final class ApiProblemResponseFactory {

    private final Clock clock;

    public ApiProblemResponseFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建一次错误响应；时间在此方法中只读取一次。
     *
     * @param status
     *            标准 HTTP 状态
     * @param errorCode
     *            稳定错误码
     * @param detail
     *            可安全外显的说明
     * @param requestId
     *            已由入口解析的请求标识
     * @param errors
     *            安全的请求 Body 字段错误
     * @return 不可变 Problem
     */
    public ApiProblemResponse create(HttpStatus status, CodeMessageEnum errorCode, String detail, String requestId,
        List<ProblemFieldError> errors) {
        HttpStatus requiredStatus = Objects.requireNonNull(status, "status must not be null");
        CodeMessageEnum requiredErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (!ApiProblemProfile.isValidRequestId(requestId)) {
            throw new IllegalArgumentException("requestId does not match the Problem Profile");
        }
        String safeDetail = detail == null || detail.isBlank() ? requiredErrorCode.getMessage() : detail;
        List<ProblemFieldError> safeErrors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
        Instant timestamp = clock.instant();
        return new ApiProblemResponse(ApiProblemProfile.TYPE, requiredStatus.getReasonPhrase(), safeDetail,
            requiredErrorCode.getCode(), requestId, ApiProblemProfile.formatTimestamp(timestamp), safeErrors);
    }

    ApiProblemResponse create(MappedProblem mappedProblem, String requestId) {
        MappedProblem requiredProblem = Objects.requireNonNull(mappedProblem, "mappedProblem must not be null");
        return create(requiredProblem.status(), requiredProblem.errorCode(), requiredProblem.detail(), requestId,
            requiredProblem.errors());
    }
}
