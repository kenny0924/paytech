package com.paytech.payment.execution.service;

import com.paytech.payment.execution.core.action.ActionResult;
import com.paytech.payment.execution.domain.PayContext;

/** 卡支付动作的创建与执行 */
public interface ActionExecutionService {
    /**
     * 执行本次新增受理的尝试
     * <li>仅在受理事务提交后调用，重复提交在 Processor 提前返回。</li>
     *
     * @param context
     *            支付上下文
     * @return 执行结果
     */
    ActionResult execute(PayContext context);
}
