package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** BFF 自有的浏览器边界错误；出站 HTTP 错误体仍由 Foundation 统一处理。 @author Codex */
public enum ClientBffErrorCode implements CodeMessageEnum {
    ORIGIN_REJECTED("CLIENT_ORIGIN_REJECTED", "请求来源不被允许"),

    UNSUPPORTED_MEDIA_TYPE("CLIENT_UNSUPPORTED_MEDIA_TYPE", "请求必须使用 JSON 内容类型"),

    DEPENDENCY_TIMEOUT("DEPENDENCY_TIMEOUT", "依赖服务响应超时，请稍后重试"),

    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂时不可用，请稍后重试"),

    WORKSPACE_CONTEXT_REQUIRED("CLIENT_WORKSPACE_CONTEXT_REQUIRED", "请先进入工作区");

    private final String code;
    private final String message;

    ClientBffErrorCode(String code, String message) {
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
