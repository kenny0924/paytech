package com.paytech.foundation.common.error;

import java.io.Serial;

/**
 * 请求参数不合法的明确失败异常，使用公共 INVALID_REQUEST 编码和 HTTP 400。
 *
 * <p>消息会向调用方外显，只能包含安全的参数说明，不得包含凭据或完整请求。
 *
 * @author Codex
 */
public class InvalidParameterException extends FailException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidParameterException() {
        this(null);
    }

    public InvalidParameterException(String message) {
        super(CommonErrorCode.INVALID_REQUEST, message);
    }

    public InvalidParameterException(String message, Throwable cause) {
        super(CommonErrorCode.INVALID_REQUEST, message, cause);
    }
}
