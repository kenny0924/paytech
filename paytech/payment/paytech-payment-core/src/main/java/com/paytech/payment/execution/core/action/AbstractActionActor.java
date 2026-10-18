package com.paytech.payment.execution.core.action;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/**
 * @author lzb
 */
public abstract class AbstractActionActor<T extends ActionContext> implements ActionActor {
    /** 支付意图 */
    protected final PaymentIntentRepository intents;
    /** 支付尝试 */
    protected final PaymentAttemptRepository attempts;

    /** 注入支付结果持久化能力。 */
    protected AbstractActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts) {
        this.intents = intents;
        this.attempts = attempts;
    }

    /** 执行动作，异常或缺失状态返回未知结果。 */
    @Override
    public final ActionResult doAction(ActionContext context, PaymentAction action) {
        try {
            ActionResult result = execute((T)context, action);
            return result == null || result.getStatus() == null ? ActionResult.unknown() : result;
        } catch (Exception e) {
            return ActionResult.unknown();
        }
    }

    /** 执行当前动作，返回完整结果，不修改传入的动作。 */
    protected abstract ActionResult execute(T context, PaymentAction action);

}
