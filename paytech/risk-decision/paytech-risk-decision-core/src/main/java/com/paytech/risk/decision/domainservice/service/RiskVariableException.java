package com.paytech.risk.decision.domainservice.service;

import com.paytech.foundation.common.enums.CodeEnum;

/** 供 HTTP 边界转换的变量业务失败；不包含 HTTP DTO 或状态实现。 */
public final class RiskVariableException extends RuntimeException {
    private final Reason reason;

    /** 保存可定位的业务失败原因，不输出变量当前值。 */
    public RiskVariableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** 返回稳定失败原因，由入口映射到公开错误合同。 */
    public Reason reason() { return reason; }

    /** 前端需要区分刷新、重新选择及只读系统变量的业务结果。 */
    public enum Reason implements CodeEnum {
        INVALID_VARIABLE_DEFINITION,

        INVALID_OPERAND,

        LEGACY_AUTHORING_NOT_SUPPORTED,

        SYSTEM_VARIABLE_READ_ONLY,

        VARIABLE_NOT_FOUND,

        VARIABLE_CODE_EXISTS,

        VARIABLE_VERSION_CONFLICT,

        VARIABLE_IN_USE,

        VARIABLE_DISABLED,

        VARIABLE_SCOPE_MISMATCH,

        VARIABLE_TYPE_MISMATCH,

        VARIABLE_UNIT_MISMATCH,

        VARIABLE_DOMAIN_MISMATCH;

        /** 返回稳定错误标识。 */
        @Override public String getCode() { return name(); }
    }
}
