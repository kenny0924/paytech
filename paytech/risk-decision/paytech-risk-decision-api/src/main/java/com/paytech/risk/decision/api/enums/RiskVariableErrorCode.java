package com.paytech.risk.decision.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 页面可分别处理的变量配置失败，不构造另一套错误响应。 */
public enum RiskVariableErrorCode implements CodeMessageEnum {
    INVALID_VARIABLE_DEFINITION("变量定义无效"),

    INVALID_OPERAND("条件操作数无效"),

    LEGACY_AUTHORING_NOT_SUPPORTED("请刷新页面并使用统一变量保存"),

    SYSTEM_VARIABLE_READ_ONLY("系统变量只允许修改名称和说明"),

    VARIABLE_NOT_FOUND("变量不存在"),

    VARIABLE_CODE_EXISTS("变量编码已存在且不能复用"),

    VARIABLE_VERSION_CONFLICT("变量已改变，请刷新后确认"),

    VARIABLE_IN_USE("变量有当前或历史引用，不能删除"),

    VARIABLE_DISABLED("变量未启用"),

    VARIABLE_SCOPE_MISMATCH("变量不适用于当前条件位置"),

    VARIABLE_TYPE_MISMATCH("变量类型不相容"),

    VARIABLE_UNIT_MISMATCH("数值量纲或币种来源不同"),

    VARIABLE_DOMAIN_MISMATCH("值不属于变量可选范围");

    private final String message;

    /** 保存可外显的安全业务说明。 */
    RiskVariableErrorCode(String message) { this.message = message; }
    /** 返回稳定 Wire 错误代码。 */
    @Override public String getCode() { return name(); }
    /** 返回默认可外显的业务说明。 */
    @Override public String getMessage() { return message; }
}
