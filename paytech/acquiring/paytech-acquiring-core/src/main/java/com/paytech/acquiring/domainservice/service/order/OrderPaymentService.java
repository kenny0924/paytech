package com.paytech.commerce.domainservice.service.order;

import com.paytech.commerce.domain.order.PaymentSucceededContext;

/** 订单支付结果的本地保存能力，已有事务时加入，否则以 REQUIRED 创建事务。 */
public interface OrderPaymentService {
    /**
     * 一次事务推进支付成功的五项事实
     * <li>支付投影成功、订单 OPEN→PAID、会话 COMPLETED、退款基数为已捕获金额、资金投影 NOT_APPLICABLE→PENDING。</li>
     * <li>任一条件更新失败抛出并发冲突并整体回滚。</li>
     */
    void applySucceeded(PaymentSucceededContext context);

    /** 订单已取消或过期时只记录支付投影，订单、会话、退款与资金投影不变。 */
    void recordClosedOrderPayment(PaymentSucceededContext context);
}
