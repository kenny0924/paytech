package com.paytech.payment.execution.core.action;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;

/**
 * @author lzb
 */
public interface ActionActor {

    /**
     * Action Code
     */
    String getCode();

    /**
     * 执行动作，返回完整结果
     * <li>传入的动作只读，结果保存成功后由 Repository 同步。</li>
     *
     * @param context
     *            上下文参数
     * @param action
     *            action
     */
    ActionResult doAction(ActionContext context, PaymentAction action);

    /**
     * Flow执行完后的事务处理
     */
    default void doInTransaction(ActionContext context, PaymentAction action) {}
}
