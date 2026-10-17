package com.paytech.foundation.caffeine.core.error;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Caffeine 本地缓存基础组件使用的稳定错误码。
 *
 * @author Codex
 */
public enum CaffeineErrorCode implements CodeMessageEnum {

    CONFIGURATION_INVALID("CAFFEINE_CACHE_CONFIGURATION_INVALID", "Caffeine本地缓存配置无效");

    private final String code;
    private final String message;

    CaffeineErrorCode(String code, String message) {
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
