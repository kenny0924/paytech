package com.paytech.risk.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 调用方需要区分处理的统一风控评估错误。 */
public enum RiskDecisionEvaluationErrorCode implements CodeMessageEnum {
    /** 本环境该风控点没有生效发布；调用方视为确定失败，不重试。 */
    RISK_DECISION_NOT_CONFIGURED("风控点尚未配置生效发布");

    private final String message;

    /** 保存可外显的安全业务说明。 */
    RiskDecisionEvaluationErrorCode(String message) { this.message = message; }

    /** 返回稳定 Wire 错误代码。 */
    @Override public String getCode() { return name(); }

    /** 返回默认可外显的业务说明。 */
    @Override public String getMessage() { return message; }
}
