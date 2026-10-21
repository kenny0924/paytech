package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 公共脚本运行的 HTTP 处理阶段。 */
public enum ScriptExecutionStage implements CodeEnum {

    /** 请求阶段，读写请求工作对象。 */
    REQUEST("REQUEST"),

    /** 响应阶段，读写响应工作副本。 */
    RESPONSE("RESPONSE");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    ScriptExecutionStage(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static ScriptExecutionStage fromCode(String code) {
        for (ScriptExecutionStage value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
