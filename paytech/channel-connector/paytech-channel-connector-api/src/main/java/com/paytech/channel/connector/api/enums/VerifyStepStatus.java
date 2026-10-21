package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 沙箱验证运行中单个步骤的结论。 */
public enum VerifyStepStatus implements CodeEnum {

    /** 步骤满足全部期望。 */
    PASSED("PASSED"),

    /** 步骤不满足期望或执行出错。 */
    FAILED("FAILED"),

    /** 前序步骤未通过，本步骤未执行。 */
    SKIPPED("SKIPPED");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    VerifyStepStatus(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static VerifyStepStatus fromCode(String code) {
        for (VerifyStepStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
