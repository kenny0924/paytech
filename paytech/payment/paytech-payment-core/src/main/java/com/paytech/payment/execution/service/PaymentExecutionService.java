package com.paytech.payment.execution.service;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.domain.ApplyCardInstrumentContext;
import com.paytech.payment.execution.domain.CreateIntentContext;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domain.TerminateIntentContext;

/** Payment 本库的支付受理原子操作。 */
public interface PaymentExecutionService {

    /**
     * 保存卡工具申请上下文
     * <li>参与调用方事务，由 Processor 与 Card 保存一起提交</li>
     */
    void saveApplyCardInstrumentContext(ApplyCardInstrumentContext context);

    /** 保存创建意图上下文 */
    void saveCreateIntentContext(CreateIntentContext context);

    /** 在本地事务中终止已确认无活动或成功尝试的支付意图。 */
    void terminateIntent(TerminateIntentContext context);

    /**
     * 读取支付尝试（查单恢复与渠道通知入口用来定位 Intent 与冻结坐标）
     *
     * @param attemptId 支付尝试 ID
     * @return 支付尝试；不存在时为 null
     */
    PaymentAttempt findAttempt(long attemptId);

    /**
     * 保存支付上下文
     * 
     * @param context
     *            上下文
     */
    void savePayContext(PayContext context);
}
