package com.paytech.risk.decision.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Client 承保的公开业务错误；HTTP 状态由 risk-decision 与 operations-console-bff 的状态解析器映射
 * <li>消费者：operations-console-web 按 Code 显示对应提示（并发、自审、状态变化、已有进行中承保、规则不再满足）。</li>
 */
public enum RiskUnderwritingErrorCode implements CodeMessageEnum {
    /** 承保记录不存在或不属于该 Client；HTTP 404。 */
    RISK_UNDERWRITING_NOT_FOUND("RISK_UNDERWRITING_NOT_FOUND", "承保记录不存在"),

    /** 该 Client 已有进行中的承保；HTTP 409。 */
    RISK_UNDERWRITING_OPEN_EXISTS("RISK_UNDERWRITING_OPEN_EXISTS", "该 Client 已有进行中的承保"),

    /** 期望版本不符或并发修改；HTTP 409。 */
    RISK_UNDERWRITING_VERSION_CONFLICT("RISK_UNDERWRITING_VERSION_CONFLICT", "承保已被其他人修改，请刷新后重试"),

    /** 当前状态不允许该操作；HTTP 409。 */
    RISK_UNDERWRITING_STATE_INVALID("RISK_UNDERWRITING_STATE_INVALID", "承保当前状态不允许该操作"),

    /** 复核人是提交人；HTTP 409。 */
    RISK_UNDERWRITING_SELF_REVIEW("RISK_UNDERWRITING_SELF_REVIEW", "不能复核自己提交的承保"),

    /** HIGH 风险承保员不能直接拒绝，须提交复核；HTTP 409。 */
    RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW("RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW", "HIGH 风险承保须由复核人拒绝"),

    /** 自动承保条件不满足或没有 ACTIVE 规则；HTTP 409。 */
    RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE("RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE", "该 Client 不满足自动承保条件");

    private final String code;

    private final String message;

    RiskUnderwritingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
