package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 沙箱验证运行的结论。 */
public enum VerifyRunStatus implements CodeEnum {

    /** 已发起，尚未得出结论。 */
    RUNNING("RUNNING"),

    /** 全部步骤满足期望。 */
    PASSED("PASSED"),

    /** 有步骤不满足期望或执行出错。 */
    FAILED("FAILED"),

    /** 执行进程中断，不再向沙箱重放。 */
    INTERRUPTED("INTERRUPTED");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    VerifyRunStatus(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static VerifyRunStatus fromCode(String code) {
        for (VerifyRunStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
