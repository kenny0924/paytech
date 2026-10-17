package com.paytech.payment.execution.core.action;

import com.paytech.payment.execution.core.enums.ActionStatus;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/** 资金授权 */
@Component
public class AuthorizeActionActor extends AbstractActionActor<PayContext> {
    /** 注入授权结果持久化能力。 */
    public AuthorizeActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts) {
        super(intents, attempts);
    }

    /** 返回动作代码。 */
    @Override
    public String getCode() {
        return ActionType.AUTHORIZE.getCode();
    }

    /** 在事务外执行原动作，具体渠道能力尚未接入。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        // TODO Channel/Card：按原 Action 幂等授权；可信成功时回填 transactionRef，未知结果恢复原授权。
        throw new ErrorException("资金授权尚未接入 Channel/Card");
    }

    /** 授权成功后记录金额，等待后续操作。 */
    @Override
    public void doInTransaction(ActionContext context, PaymentAction action) {
        assert action.statusIn(ActionStatus.SUCCEEDED);
        attempts.authorizeCompleted(context.getAttempt(), action.getAmount());
    }
}
