package com.paytech.payment.integration.risk.request;

import lombok.Getter;
import lombok.Setter;

/** Risk 支付评估请求（草案）；只含交易维度，不含卡值或个人信息。 */
@Getter
@Setter
public class PaymentRiskAssessmentRequestBody {
    /** 支付尝试 ID，作为评估的业务身份 */
    private String paymentAttemptId;

    /** 风控动作 ID */
    private String paymentActionId;

    /** Client ID */
    private String clientId;

    /** 经营主体编号 */
    private String entityNo;

    /** 交易金额，主单位十进制字符串 */
    private String amount;

    /** 交易币种 */
    private String currency;

    /** 支付方式代码 */
    private String paymentMethodCode;

    /** 消费者国家 */
    private String countryCode;
}
