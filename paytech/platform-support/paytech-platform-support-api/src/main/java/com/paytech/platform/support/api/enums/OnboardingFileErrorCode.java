package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 文件错误只包含安全提示，不暴露他人的目标或存储坐标。 */
public enum OnboardingFileErrorCode implements CodeMessageEnum {
    FILE_NOT_FOUND("文件目标不可访问"),

    FILE_TARGET_CONFLICT("申请与入驻工作区不匹配"),

    FILE_STATE_CONFLICT("当前文件状态不允许此操作，请刷新申请"),

    FILE_POLICY_REJECTED("文件类型或大小不符合要求");

    private final String message;
    /** 绑定安全提示。 */
    OnboardingFileErrorCode(String message) { this.message = message; }
    /** 返回稳定代码。 */
    @Override public String getCode() { return name(); }
    /** 返回安全文案。 */
    @Override public String getMessage() { return message; }
}
