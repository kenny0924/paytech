package com.paytech.foundation.internalauth.api;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

/**
 * Token 不可接受（{@code INTERNAL_TOKEN_INVALID}，MVC 映射为 401）；消息只含安全的原因分类，不含 Token 原文。
 *
 * @author Claude
 */
public final class InternalTokenInvalidException extends FailException {

    /**
     * 创建校验失败。
     *
     * @param reason 安全原因，例如 {@code audience mismatch}
     */
    public InternalTokenInvalidException(String reason) {
        super(CommonErrorCode.INTERNAL_TOKEN_INVALID, "内部调用凭证无效: " + reason);
    }

    /**
     * 创建保留原因的校验失败。
     *
     * @param reason 安全原因
     * @param cause 原始异常
     */
    public InternalTokenInvalidException(String reason, Throwable cause) {
        super(CommonErrorCode.INTERNAL_TOKEN_INVALID, "内部调用凭证无效: " + reason, cause);
    }
}
