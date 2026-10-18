package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 原子 API 节点与标准 API 一次执行的结果状态。 */
public enum ExecuteStatus implements CodeEnum {

    /** 执行成功，按 SUCCESS 分支输出。 */
    SUCCESS("SUCCESS"),

    /** 渠道明确失败，按 FAIL 分支输出。 */
    FAIL("FAIL"),

    /** 结果无法确认，输出为空对象；不能据此判定渠道未执行。 */
    ERROR("ERROR");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    ExecuteStatus(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static ExecuteStatus fromCode(String code) {
        for (ExecuteStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
