package com.paytech.foundation.compensation.core.error;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 补偿组件的稳定错误码。
 *
 * @author Codex
 */
public enum CompensationErrorCode implements CodeMessageEnum {

    CONFIGURATION_INVALID("COMPENSATION_CONFIGURATION_INVALID", "补偿组件配置无效"),
    EVENT_CONFLICT("COMPENSATION_EVENT_CONFLICT", "补偿事件与现有记录冲突"),
    RECOVERY_REJECTED("COMPENSATION_RECOVERY_REJECTED", "补偿事件人工恢复请求被拒绝");

    private final String code;
    private final String message;

    CompensationErrorCode(String code, String message) {
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
