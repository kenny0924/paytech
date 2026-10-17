package com.paytech.foundation.common.error;

import java.io.Serial;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 兼容既有业务异常名称的明确失败异常。
 *
 * <p>
 * 普通业务拒绝优先使用 {@link BizCheckFailException}，无需新增业务异常子类。
 *
 * @author Codex
 */
public class BusinessException extends FailException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException(CodeMessageEnum errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(CodeMessageEnum errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
