package com.paytech.vault.client;

import com.paytech.foundation.common.enums.CodeMessageEnum;

import lombok.AllArgsConstructor;

/** 协议发布的稳定错误 Code；绝不采用远端自由文本消息。 */
@AllArgsConstructor
public enum VaultClientErrorCode implements CodeMessageEnum {
    INVALID_REQUEST("INVALID_REQUEST", "请求参数无效"),

    VALUE_TOO_LARGE("VALUE_TOO_LARGE", "值超过32KiB限制"),

    UNSUPPORTED_DATA_TYPE("UNSUPPORTED_DATA_TYPE", "不支持的数据类型"),

    TOKEN_MODE_NOT_ALLOWED("TOKEN_MODE_NOT_ALLOWED", "Token模式不允许"),

    ACCESS_DENIED("ACCESS_DENIED", "访问被拒绝"),

    TOKEN_NOT_FOUND("TOKEN_NOT_FOUND", "Token不存在"),

    IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_KEY_CONFLICT", "幂等键已被不同请求使用"),

    INTEGRITY_CHECK_FAILED("INTEGRITY_CHECK_FAILED", "完整性校验失败"),

    INTERNAL_ERROR("INTERNAL_ERROR", "服务内部错误"),

    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂不可用"),

    AUDIT_UNAVAILABLE("AUDIT_UNAVAILABLE", "访问审计不可用"),

    POLICY_VERSION_CONFLICT("POLICY_VERSION_CONFLICT", "策略已被他人修改，请刷新后重试"),

    POLICY_ENTRY_EXISTS("POLICY_ENTRY_EXISTS", "策略条目已存在"),

    POLICY_ENTRY_NOT_FOUND("POLICY_ENTRY_NOT_FOUND", "策略条目不存在"),

    VALUE_NOT_JSON_OBJECT("VALUE_NOT_JSON_OBJECT", "值不是合法的JSON对象"),

    VALUE_PATTERN_MISMATCH("VALUE_PATTERN_MISMATCH", "值不符合数据类型的正则"),

    VALUE_PATTERN_INVALID("VALUE_PATTERN_INVALID", "值格式与正则的组合无效或正则无法编译"),

    SAMPLE_VALUE_INVALID("SAMPLE_VALUE_INVALID", "测试样例缺失或不符合值格式"),

    /** 内部调用 Token 缺失或不合法（Vault 以 REQUIRED 模式校验时）。 */
    INTERNAL_TOKEN_INVALID("INTERNAL_TOKEN_INVALID", "内部调用凭证无效");

    private final String code;
    private final String message;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
