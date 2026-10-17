package com.paytech.payment.execution.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.payment.execution.core.action.ActionType;

import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.RiskResult;
import lombok.Getter;
import lombok.Setter;

/** t_payment_action 的完整持久化映射；不通过 HTTP 暴露。 */
@Getter
@Setter
public class PaymentAction {
    /** 支付动作标识。 */
    private Long paymentActionId;

    /** 支付尝试标识。 */
    private Long paymentAttemptId;

    /** 渠道退款标识。 */
    private Long channelRefundId;

    /** 重复支付处置标识。 */
    private Long duplicatePaymentCaseId;

    /** 动作序号。 */
    private Integer actionNo;

    /** 动作类型。 */
    private ActionType actionType;

    /** 动作状态。 */
    private ActionStatus actionStatus;

    /** 目标交易引用标识。 */
    private Long targetChannelTxnRefId;

    /** 业务金额。 */
    private BigDecimal amount;

    /** 币种代码。 */
    private String currency;

    /** 渠道幂等键 */
    private String channelIdempotencyKey;

    /** 交易风控评估标识。 */
    private Long transactionAssessmentId;

    /** 交易风控结论。 */
    private RiskResult riskResult;

    /** 认证要求。 */
    private AuthenticationRequirement authenticationRequirement;

    /** 交互类型。 */
    private InteractionType interactionType;

    /** 交互截止时间。 */
    private Instant interactionExpiresAt;

    /** 渠道响应代码。 */
    private String channelResponseCode;

    /** 渠道响应说明。 */
    private String channelResponseMsg;

    /** 渠道结果时间 */
    private Instant channelResultTime;

    /** 乐观锁版本。 */
    private Long version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    public boolean isFinal() {
        return actionStatus.isFinal();
    }

    public boolean isRunning() {
        return !isFinal();
    }

    public boolean statusIn(ActionStatus... statuses) {
        for (ActionStatus status : statuses) {
            if (this.actionStatus == status) {
                return true;
            }
        }
        return false;
    }
}
