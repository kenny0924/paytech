package com.paytech.foundation.common.error;

import java.io.Serial;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 依赖暂时不可用的系统异常，MVC 固定返回 HTTP 503 和错误码的安全默认消息。
 *
 * <p>普通依赖故障使用无参或 Cause 构造器；只有调用方明确按错误码分支时才使用业务枚举。
 *
 * @author Codex
 */
public class ServiceUnavailableException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException() {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, null, cause);
    }

    public ServiceUnavailableException(CodeMessageEnum errorCode) {
        super(errorCode);
    }

    public ServiceUnavailableException(CodeMessageEnum errorCode, Throwable cause) {
        super(errorCode, null, cause);
    }
}
