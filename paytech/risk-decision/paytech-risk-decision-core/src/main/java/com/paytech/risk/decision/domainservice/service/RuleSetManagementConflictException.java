package com.paytech.risk.decision.domainservice.service;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.enums.CodeEnum;

/** Owner 内部的管理冲突；Web 将明确原因映射为已发布错误合同。 */
public final class RuleSetManagementConflictException extends ConcurrentRequestException {
    private final Reason reason;

    /** 保留可安全外显的业务说明及用于边界转换的原因。 */
    public RuleSetManagementConflictException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** 返回稳定原因，调用方不得依据消息文字分支。 */
    public Reason reason() {
        return reason;
    }

    /** 三种需要页面分别处理的管理冲突。 */
    public enum Reason implements CodeEnum {
        IN_USE,

        CHANGED,

        DELETED;

        /** 返回显式定义的内部代码。 */
        @Override
        public String getCode() {
            return switch (this) {
                case IN_USE -> "IN_USE";
                case CHANGED -> "CHANGED";
                case DELETED -> "DELETED";
            };
        }
    }
}
