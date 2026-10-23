package com.paytech.acquiring.domainservice.service.order;

import com.paytech.acquiring.domain.order.OrderPaymentEventSource;

/** OrderPaymentSucceeded 发布所需本库事实的只读加载；不加锁、不开事务、不调用外部服务。 */
public interface OrderPaymentEventService {
    /**
     * 读取订单冻结商业字段与订单支付投影
     *
     * @param orderId 订单 ID
     * @return 两项事实，各自不存在时为空，由调用方判定能否发布
     */
    OrderPaymentEventSource loadEventSource(long orderId);
}
