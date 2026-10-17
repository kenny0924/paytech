package com.paytech.platform.support.api.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 文件 Owner 发布的稳定代码；HTTP 字段仍使用 String，未知代码由消费方明确拒绝。 */
public enum OnboardingFilePurpose implements CodeEnum {
    ASSOCIATE("ASSOCIATE"),

    READ("READ");
    private final String code;
    /** 固定发布代码，不依赖 Java 常量名。 */
    OnboardingFilePurpose(String code) { this.code=code; }
    /** 返回稳定代码。 */
    @Override public String getCode() { return code; }
    /** 显式拒绝未知代码，不映射成默认许可。 */
    public static OnboardingFilePurpose fromCode(String code) {
        for (var value:values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("未知文件代码");
    }
}
