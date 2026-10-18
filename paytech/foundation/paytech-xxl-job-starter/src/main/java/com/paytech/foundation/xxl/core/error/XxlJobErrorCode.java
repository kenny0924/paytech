package com.paytech.foundation.xxl.core.error;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * XXL-Job 基础组件使用的稳定错误码。
 *
 * @author Codex
 */
public enum XxlJobErrorCode implements CodeMessageEnum {

    CONFIGURATION_INVALID("XXL_JOB_CONFIGURATION_INVALID", "XXL-Job配置无效");

    private final String code;
    private final String message;

    XxlJobErrorCode(String code, String message) {
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
