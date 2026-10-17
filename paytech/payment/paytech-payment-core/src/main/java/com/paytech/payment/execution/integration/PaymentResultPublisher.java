package com.paytech.payment.execution.integration;

import com.paytech.payment.execution.domain.PaymentSucceededResult;

/** 支付结果业务事实的出站端口，由 Web 组合根以 RabbitMQ 实现；只能在数据库事务外调用。 */
public interface PaymentResultPublisher {
    /**
     * 发布支付成功
     * <li>收到 Broker 确认才返回；未确认、退回或超时抛出运行时异常，由补偿任务重试。</li>
     *
     * @param result 已成功收款的支付尝试
     */
    void publishSucceeded(PaymentSucceededResult result);
}
