package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** BFF 自有的浏览器边界错误；出站 HTTP 错误体仍由 Foundation 统一处理。 @author Codex */
public enum OperationsBffErrorCode implements CodeMessageEnum {
    ORIGIN_REJECTED("OPERATIONS_ORIGIN_REJECTED", "请求来源不被允许"),

    UNSUPPORTED_MEDIA_TYPE("OPERATIONS_UNSUPPORTED_MEDIA_TYPE", "请求必须使用 JSON 内容类型"),

    DEPENDENCY_TIMEOUT("DEPENDENCY_TIMEOUT", "依赖服务响应超时，请稍后重试"),

    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用，请稍后重试");

    private final String code;
    private final String message;

    OperationsBffErrorCode(String code, String message) {
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
