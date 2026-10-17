package com.paytech.foundation.idempotency.api;

import java.util.Arrays;

import com.paytech.foundation.common.enums.CodeEnum;

/** 命令幂等机制拥有的闭合技术状态。 */
public enum CommandProcessStatus implements CodeEnum {
    PROCESSING("PROCESSING"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED");

    private final String code;

    CommandProcessStatus(String code) {
        this.code = code;
    }

    /** 将持久化 Code 转换为受控状态，未知值直接失败。 */
    public static CommandProcessStatus fromCode(String code) {
        return Arrays.stream(values()).filter(status -> status.code.equals(code)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知命令处理状态: " + code));
    }

    @Override
    public String getCode() {
        return code;
    }
}
