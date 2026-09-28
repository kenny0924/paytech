package com.paytech.foundation.redis.core.error;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Redis 基础组件使用的稳定错误码。
 *
 * @author Codex
 */
public enum RedisErrorCode implements CodeMessageEnum {

    CONFIGURATION_INVALID("REDIS_CONFIGURATION_INVALID", "Redis配置无效"),
    DEPLOYMENT_MODE_UNSUPPORTED("REDIS_DEPLOYMENT_MODE_UNSUPPORTED", "Redis部署模式暂不支持"),
    CONNECTION_UNAVAILABLE("REDIS_CONNECTION_UNAVAILABLE", "Redis连接不可用"),
    KEY_NAMESPACE_MISMATCH("REDIS_KEY_NAMESPACE_MISMATCH", "Redis Key Namespace不匹配"),
    JSON_SERIALIZATION_FAILED("REDIS_JSON_SERIALIZATION_FAILED", "Redis JSON序列化失败"),
    JSON_DESERIALIZATION_FAILED("REDIS_JSON_DESERIALIZATION_FAILED", "Redis JSON反序列化失败"),
    CACHE_VALUE_TYPE_MISMATCH("REDIS_CACHE_VALUE_TYPE_MISMATCH", "Redis Cache值类型不匹配"),
    OPERATION_ARGUMENT_INVALID("REDIS_OPERATION_ARGUMENT_INVALID", "Redis操作参数不正确"),
    OPERATION_UNSUPPORTED("REDIS_OPERATION_UNSUPPORTED", "Redis操作不受支持");

    private final String code;
    private final String message;

    RedisErrorCode(String code, String message) {
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
