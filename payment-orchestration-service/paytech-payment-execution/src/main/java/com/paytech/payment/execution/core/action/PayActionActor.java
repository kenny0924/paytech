package com.paytech.payment.execution.core.action;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/** 直接收款 */
@Component
public class PayActionActor extends AbstractActionActor<PayContext> {
    /** 注入收款结果持久化能力。 */
    public PayActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts) {
        super(intents, attempts);
    }

    /** 返回动作代码。 */
    @Override
    public String getCode() {
        return ActionType.PAY.getCode();
    }

    /** 在事务外执行原动作，具体渠道能力尚未接入。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        // TODO Channel/Card：按原 Action 幂等收款；可信成功时回填 transactionRef，未知结果恢复原付款。
        throw new ErrorException("直接收款尚未接入 Channel/Card");
    }

    /** 收款成功后完成 Attempt 和 Intent。 */
    @Override
    public void doInTransaction(ActionContext context, PaymentAction action) {
        assert action.statusIn(ActionStatus.SUCCEEDED);
        var attempt = context.getAttempt();
        attempts.payCompleted(attempt, action.getAmount());
        intents.attemptSucceed(context.getIntent(), attempt.getPaymentAttemptId(), IntentStatus.SUCCEEDED);
    }
}
