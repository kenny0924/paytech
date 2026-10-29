package com.paytech.risk.control.domain.underwriting;

import java.io.Serial;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;

/** 承保业务失败；Web 按明确原因映射已发布的 RiskUnderwritingErrorCode，禁止解析错误文案。 */
public final class UnderwritingException extends BizCheckFailException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 失败原因，与 RiskUnderwritingErrorCode 一一对应。 */
    public enum Reason implements CodeEnum {
        /** 记录不存在。 */
        NOT_FOUND("NOT_FOUND"),

        /** 已有进行中承保。 */
        OPEN_EXISTS("OPEN_EXISTS"),

        /** 期望版本不符或并发修改。 */
        VERSION_CONFLICT("VERSION_CONFLICT"),

        /** 状态不允许。 */
        STATE_INVALID("STATE_INVALID"),

        /** 复核人是提交人。 */
        SELF_REVIEW("SELF_REVIEW"),

        /** HIGH 风险提交时拒绝。 */
        DECLINE_REQUIRES_REVIEW("DECLINE_REQUIRES_REVIEW"),

        /** 不满足自动承保。 */
        AUTO_NOT_ELIGIBLE("AUTO_NOT_ELIGIBLE");

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
     * 构造承保失败
     *
     * @param reason 失败原因
     * @param message 可安全外显的中文说明，含必要业务上下文
     */
    public UnderwritingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** 失败原因。 */
    public Reason reason() {
        return reason;
    }
}
