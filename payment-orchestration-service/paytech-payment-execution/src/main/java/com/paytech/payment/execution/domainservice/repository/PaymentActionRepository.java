package com.paytech.payment.execution.domainservice.repository;

import com.paytech.payment.execution.core.action.ActionResult;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;

/** 支付动作数据访问 */
public interface PaymentActionRepository {
    /** 创建一条动作，序号及幂等键由唯一约束保护。 */
    void insertAction(PaymentAction action);

    /** 按原状态和版本保存完整结果，更新成功后同步原动作及版本和时间。 */
    void saveResult(PaymentAction action, ActionResult result);

    /** 读取原动作；不存在时返回空。 */
    PaymentAction selectById(Long activeActionId);
}
