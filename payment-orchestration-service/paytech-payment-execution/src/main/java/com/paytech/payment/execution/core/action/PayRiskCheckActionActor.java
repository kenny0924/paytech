package com.paytech.payment.execution.core.action;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/**
 * @author lzb
 */
@Component
public class PayRiskCheckActionActor extends AbstractActionActor<PayContext> {

    /** 注入风控结果持久化能力。 */
    public PayRiskCheckActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts) {
        super(intents, attempts);
    }

    /** 返回支付风控动作代码。 */
    @Override
    public String getCode() {
        return ActionType.PAY_RISK.getCode();
    }

    /** 按原业务身份执行或恢复风控，不在此选择后继动作。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        // 当风控失败的时候，return ActionResult.failed();
        // TODO Risk：按原 Action/Attempt 恢复评估，返回真实 assessmentId、风险结论及认证要求。
        throw new ErrorException("支付风控尚未接入 Risk");
    }
}
