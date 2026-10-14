package com.paytech.payment.execution.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 支付风控评估输入：以支付尝试为评估身份，只含交易维度，不含卡值或个人信息。 */
@Getter
@Setter
public class PaymentRiskCommand {
    /** 支付尝试 ID，重试同一 Attempt 的评估沿用此身份 */
    private Long paymentAttemptId;

    /** 风控动作 ID */
    private Long paymentActionId;

    /** Client ID */
    private Long clientId;

    /** 经营主体编号 */
    private String entityNo;

    /** 交易金额（主单位） */
    private BigDecimal amount;

    /** 交易币种 */
    private String currency;

    /** 支付方式代码 */
    private String paymentMethodCode;

    /** 消费者国家 */
    private String countryCode;
}
