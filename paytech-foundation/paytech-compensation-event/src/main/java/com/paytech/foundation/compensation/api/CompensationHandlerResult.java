package com.paytech.foundation.compensation.api;

import java.util.regex.Pattern;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 处理器的明确执行结果。
 *
 * @param outcome
 *            结果分类
 * @param errorCode
 *            稳定的技术或业务错误码
 * @param safeSummary
 *            不含载荷敏感内容的诊断摘要
 * @author Codex
 */
public record CompensationHandlerResult(Outcome outcome, String errorCode, String safeSummary) {

    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    public CompensationHandlerResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome == Outcome.SUCCESS) {
            errorCode = null;
            safeSummary = null;
        } else {
            errorCode = requireText("errorCode", errorCode, 64);
            if (!ERROR_CODE.matcher(errorCode).matches()) {
                throw new IllegalArgumentException("errorCode must match [A-Z][A-Z0-9_-]{0,63}");
            }
            safeSummary = requireText("safeSummary", safeSummary, 1000);
        }
    }

    public static CompensationHandlerResult success() {
        return new CompensationHandlerResult(Outcome.SUCCESS, null, null);
    }

    public static CompensationHandlerResult retryableFailure(String errorCode, String safeSummary) {
        return new CompensationHandlerResult(Outcome.RETRYABLE_FAILURE, errorCode, safeSummary);
    }

    public static CompensationHandlerResult terminalFailure(String errorCode, String safeSummary) {
        return new CompensationHandlerResult(Outcome.TERMINAL_FAILURE, errorCode, safeSummary);
    }

    private static String requireText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        return trimmed;
    }

    /**
     * 支持的执行结果分类。
     */
    public enum Outcome implements CodeEnum {

        SUCCESS("SUCCESS"),
        RETRYABLE_FAILURE("RETRYABLE_FAILURE"),
        TERMINAL_FAILURE("TERMINAL_FAILURE");

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }
}
