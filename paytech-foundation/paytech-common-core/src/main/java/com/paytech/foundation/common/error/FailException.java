package com.paytech.foundation.common.error;

import java.io.Serial;
import java.util.Objects;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 表示结果已经明确失败、不能自动重试的异常。
 *
 * <p>
 * 只有已经能够确定业务结果的场景才能抛出该异常。数据库超时、网络中断等结果未知场景不得包装为该异常。
 * 普通业务拒绝优先使用 {@link BizCheckFailException}，参数和并发冲突使用对应公共类别；
 * 仅调用方需要明确分支的契约才定义独立错误码。消息会安全外显，不能包含内部诊断或敏感数据。
 *
 * @author Codex
 */
public class FailException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CodeMessageEnum errorCode;

    public FailException(String message) {
        this(CommonErrorCode.BUSINESS_CHECK_FAILED, message, null);
    }

    public FailException(CodeMessageEnum errorCode) {
        this(errorCode, null, null);
    }

    public FailException(CodeMessageEnum errorCode, String message) {
        this(errorCode, message, null);
    }

    public FailException(CodeMessageEnum errorCode, String message, Throwable cause) {
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

    /**
     * 兼容既有调用方式。
     *
     * @return 错误码定义
     */
    public CodeMessageEnum errorCode() {
        return errorCode;
    }
}
