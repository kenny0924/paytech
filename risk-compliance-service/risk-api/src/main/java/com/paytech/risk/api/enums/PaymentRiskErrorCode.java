package com.paytech.risk.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Payment 需要区分处理的支付风控评估错误。 */
public enum PaymentRiskErrorCode implements CodeMessageEnum {
    /** 本环境没有支付决策点的生效发布；Payment 视为确定失败，不重试。 */
    PAYMENT_RISK_NOT_CONFIGURED("支付风控尚未配置生效发布");

    private final String message;

    /** 保存可外显的安全业务说明。 */
    PaymentRiskErrorCode(String message) { this.message = message; }

    /** 返回稳定 Wire 错误代码。 */
    @Override public String getCode() { return name(); }

    /** 返回默认可外显的业务说明。 */
    @Override public String getMessage() { return message; }
}
