package com.paytech.payment.execution.core.action;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/** 付款人认证 */
@Component
public class AuthenticateActionActor extends AbstractActionActor<PayContext> {
    /** 注入认证结果持久化能力。 */
    public AuthenticateActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts) {
        super(intents, attempts);
    }

    /** 返回动作代码。 */
    @Override
    public String getCode() {
        return ActionType.AUTHENTICATE.getCode();
    }

    /** 在事务外执行原动作，具体渠道能力尚未接入。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        // TODO Channel/Card：按原 Action 恢复认证；等待交互保持 PROCESSING，满足原风控要求后才成功。
        throw new ErrorException("付款人认证尚未接入 Channel/Card");
    }
}
