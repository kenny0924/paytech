package com.paytech.foundation.common.error;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 公共基础库提供的稳定错误码。
 *
 * @author Codex
 */
public enum CommonErrorCode implements CodeMessageEnum {

    INVALID_REQUEST("INVALID_REQUEST", "请求参数不正确"),

    VALIDATION_ERROR("VALIDATION_ERROR", "请求字段校验未通过"),

    NOT_FOUND("NOT_FOUND", "资源不存在"),

    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不受支持"),

    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不受支持"),

    BUSINESS_CHECK_FAILED("BUSINESS_CHECK_FAILED", "业务校验未通过"),

    CONCURRENT_REQUEST("CONCURRENT_REQUEST", "请求发生并发冲突，请稍后重试"),

    INTERNAL_TOKEN_INVALID("INTERNAL_TOKEN_INVALID", "内部调用凭证无效"),

    INTERNAL_ERROR("INTERNAL_ERROR", "系统处理失败"),

    UPSTREAM_INVALID_RESPONSE("UPSTREAM_INVALID_RESPONSE", "依赖服务响应异常，请稍后重试"),

    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
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
