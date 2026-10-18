package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 验证用例的验证状态，由最近的运行与当前配置版本比较得出，不单独保存。 */
public enum VerifyCaseStatus implements CodeEnum {

    /** 没有运行记录。 */
    NOT_RUN("NOT_RUN"),

    /** 最近一次运行尚未得出结论。 */
    RUNNING("RUNNING"),

    /** 最近一次完成的运行通过，且其后用例、账号与引用配置均未变更。 */
    PASSED("PASSED"),

    /** 最近一次完成的运行未通过或已中断。 */
    FAILED("FAILED"),

    /** 最近一次完成的运行通过，但用例、账号或引用配置已变更，需要重新验证。 */
    STALE("STALE");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    VerifyCaseStatus(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
