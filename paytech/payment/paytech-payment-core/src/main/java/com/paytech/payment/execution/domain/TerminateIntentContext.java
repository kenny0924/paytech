package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.TerminationDecision;
import com.paytech.payment.execution.core.enums.TerminationReason;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 终止支付意图上下文 */
@Getter
@AllArgsConstructor
public class TerminateIntentContext {
    /** 支付意图 */
    private final PaymentIntent intent;

    /**
     * 活动支付尝试
     * <li>Intent 没有活动尝试时为空。</li>
     */
    private final PaymentAttempt attempt;

    /**
     * 终止原因
     * <li>安全终止时按原因写入 CANCELLED 或 EXPIRED。</li>
     */
    private final TerminationReason reason;

    /** 根据已加载的资金事实判断能否终止，不改变对象状态。 */
    public TerminationDecision terminationDecision() {
        if (intent.getIntentStatus() == IntentStatus.SUCCEEDED || intent.getSuccessfulAttemptId() != null) {
            return TerminationDecision.ALREADY_PAID;
        }
        if (intent.statusIn(IntentStatus.CANCELLED, IntentStatus.EXPIRED)) {
            return TerminationDecision.ALREADY_TERMINATED;
        }
        if (attempt == null) {
            return TerminationDecision.TERMINATED;
        }
        if (attempt.getAttemptStatus() == AttemptStatus.SUCCEEDED || attempt.getCapturedAmount().signum() > 0) {
            return TerminationDecision.ALREADY_PAID;
        }
        if (attempt.getActiveActionId() != null) {
            return TerminationDecision.PENDING;
        }
        if (attempt.getAuthorizedAmount().signum() > 0) {
            return TerminationDecision.REVERSAL_REQUIRED;
        }
        // 受理后尚未创建 Action 也属于未决尝试，不能仅凭空执行位终止。
        return TerminationDecision.PENDING;
    }
}
