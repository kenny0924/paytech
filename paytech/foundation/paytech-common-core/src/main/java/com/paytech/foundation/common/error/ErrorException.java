package com.paytech.foundation.common.error;

import java.io.Serial;
import java.util.Objects;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 表示结果未知、允许按调用方策略进行有界重试的异常。
 *
 * <p>
 * 是否继续重试以及重试次数由调用方策略决定；该异常本身不执行重试。
 * 普通系统异常复用 INTERNAL_ERROR；MVC 不外显实例消息或 Cause，只返回错误码的安全默认消息。
 *
 * @author Codex
 */
public class ErrorException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CodeMessageEnum errorCode;

    public ErrorException(String message) {
        this(CommonErrorCode.INTERNAL_ERROR, message, null);
    }

    public ErrorException(String message, Throwable cause) {
        this(CommonErrorCode.INTERNAL_ERROR, message, cause);
    }

    public ErrorException(Throwable cause) {
        this(CommonErrorCode.INTERNAL_ERROR, null, cause);
    }

    public ErrorException(CodeMessageEnum errorCode) {
        this(errorCode, null, null);
    }

    public ErrorException(CodeMessageEnum errorCode, String message) {
        this(errorCode, message, null);
    }

    public ErrorException(CodeMessageEnum errorCode, String message, Throwable cause) {
        super(resolveMessage(errorCode, message), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    private static String resolveMessage(CodeMessageEnum errorCode, String message) {
        CodeMessageEnum requiredErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        return message == null || message.isBlank() ? requiredErrorCode.getMessage() : message;
    }

    /**
     * 获取错误码定义。
     *
     * @return 错误码定义
     */
    public CodeMessageEnum getErrorCode() {
        return errorCode;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 稳定错误码
     */
    public String getCode() {
        return errorCode.getCode();
    }
}
