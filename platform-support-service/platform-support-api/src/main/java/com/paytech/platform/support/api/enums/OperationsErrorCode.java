package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 调用方需要据此处理登录、会话和权限的 Operations 错误契约。 */
public enum OperationsErrorCode implements CodeMessageEnum {
    OPERATIONS_AUTHENTICATION_FAILED("OPERATIONS_AUTHENTICATION_FAILED", "账号或凭据无效"),

    OPERATIONS_SESSION_INVALID("OPERATIONS_SESSION_INVALID", "登录已失效，请重新登录"),

    OPERATIONS_PERMISSION_DENIED("OPERATIONS_PERMISSION_DENIED", "没有执行此操作的权限");

    private final String code;
    private final String message;

    OperationsErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
