package com.paytech.risk.control.domain.limittier;

import java.io.Serial;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;

/** 限额档位业务失败；Web 按明确原因映射已发布的 RiskLimitTierErrorCode，禁止解析错误文案。 */
public final class LimitTierException extends BizCheckFailException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 失败原因，与 RiskLimitTierErrorCode 一一对应。 */
    public enum Reason implements CodeEnum {
        /** Client 没有档位。 */
        NOT_FOUND("NOT_FOUND"),

        /** 申请不存在。 */
        CHANGE_NOT_FOUND("CHANGE_NOT_FOUND"),

        /** 不允许的档位迁移。 */
        TRANSITION_INVALID("TRANSITION_INVALID"),

        /** 已有待审批申请。 */
        PENDING_EXISTS("PENDING_EXISTS"),

        /** 期望版本不符或并发修改。 */
        VERSION_CONFLICT("VERSION_CONFLICT"),

        /** 上线复核清单未满足。 */
        CHECKLIST_NOT_SATISFIED("CHECKLIST_NOT_SATISFIED"),

        /** 高级审批级别的申请走了普通审批接口。 */
        ELEVATION_REQUIRED("ELEVATION_REQUIRED"),

        /** 审批人是申请人。 */
        SELF_APPROVAL("SELF_APPROVAL"),

        /** 撤回人不是申请人。 */
        NOT_REQUESTER("NOT_REQUESTER"),

        /** 申请状态不允许。 */
        CHANGE_STATE_INVALID("CHANGE_STATE_INVALID"),

        /** 迁移调用方不在白名单。 */
        MIGRATION_FORBIDDEN("MIGRATION_FORBIDDEN");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    private final Reason reason;

    /**
     * 构造档位失败
     *
     * @param reason 失败原因
     * @param message 可安全外显的中文说明，含必要业务上下文
     */
    public LimitTierException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** 失败原因。 */
    public Reason reason() {
        return reason;
    }
}
