package com.paytech.acquiring.domainservice.service.order.impl;

import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.order.OrderPaymentEventSource;
import com.paytech.acquiring.domainservice.repository.order.OrderContextRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.service.order.OrderPaymentEventService;

import lombok.AllArgsConstructor;

/** 两次独立只读查询：两者都是订单进入 PAID 后不再变化的事实，相邻提交时点不影响载荷，不需要一致性快照。 */
@Service
@AllArgsConstructor
public class OrderPaymentEventServiceImpl implements OrderPaymentEventService {
    private final OrderContextRepository orderContextRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;

    /** 三表商业字段与支付投影分别读取。 */
    @Override
    public OrderPaymentEventSource loadEventSource(long orderId) {
        return new OrderPaymentEventSource(orderContextRepository.findCommercialContext(orderId),
            orderPaymentProjectionRepository.findByOrderId(orderId));
    }
}
