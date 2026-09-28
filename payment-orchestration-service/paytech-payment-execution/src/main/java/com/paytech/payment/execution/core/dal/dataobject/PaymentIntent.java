package com.paytech.payment.execution.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.payment.execution.core.enums.IntentStatus;

import lombok.Getter;
import lombok.Setter;

/** t_payment_intent 的完整持久化映射；不通过 HTTP 暴露。 */
@Getter
@Setter
public class PaymentIntent {
    /** 支付意图标识。 */
    private Long paymentIntentId;

    /** 来源类型。 */
    private String sourceType;

    /** 来源业务标识。 */
    private Long sourceId;

    /** Client 标识。 */
    private Long clientId;

    /** 经营主体编号。 */
    private String entityNo;

    /** 客户定价版本标识。 */
    private Long clientPricingVersionId;

    /** 合作伙伴分成版本标识。 */
    private Long partnerShareVersionId;

    /** 平台服务费版本标识。 */
    private Long platformChargeVersionId;

    /** 行业代码。 */
    private String industryCode;

    /** MCC 代码。 */
    private String mccCode;

    /** 消费者国家。 */
    private String consumerCountryCode;

    /** 业务金额。 */
    private BigDecimal amount;

    /** 币种代码。 */
    private String currency;

    /** 支付意图状态。 */
    private IntentStatus intentStatus;

    /** 活动支付尝试标识。 */
    private Long activeAttemptId;

    /** 成功支付尝试标识。 */
    private Long successfulAttemptId;

    /** 业务到期时间。 */
    private Instant expiresAt;

    /** 乐观锁版本。 */
    private Long version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    public boolean statusIn(IntentStatus... statuses) {
        for (IntentStatus status : statuses) {
            if (this.intentStatus == status) {
                return true;
            }
        }
        return false;
    }

    public boolean hasActiveAttempt() {
        return activeAttemptId != null;
    }
}
