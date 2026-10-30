package com.paytech.acquiring.domainservice.service.order.impl;

import org.springframework.stereotype.Service;

import com.paytech.acquiring.domain.order.OrderPaymentEventSource;
import com.paytech.acquiring.domainservice.repository.order.OrderContextRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderPaymentProjectionRepository;
import com.paytech.acquiring.domainservice.repository.order.OrderTaxCalculationRepository;
import com.paytech.acquiring.domainservice.service.order.OrderPaymentEventService;

import lombok.AllArgsConstructor;

/** 三次独立只读查询：都是订单进入 PAID 后不再变化的事实（消费者接受后报价不再追加），相邻提交时点不影响载荷，不需要一致性快照。 */
@Service
@AllArgsConstructor
public class OrderPaymentEventServiceImpl implements OrderPaymentEventService {
    private final OrderContextRepository orderContextRepository;
    private final OrderPaymentProjectionRepository orderPaymentProjectionRepository;
    private final OrderTaxCalculationRepository orderTaxCalculationRepository;

    /** 三表商业字段、支付投影与当前报价的税务计算分别读取。 */
    @Override
    public OrderPaymentEventSource loadEventSource(long orderId) {
        return new OrderPaymentEventSource(orderContextRepository.findCommercialContext(orderId),
            orderPaymentProjectionRepository.findByOrderId(orderId),
            orderTaxCalculationRepository.findLatestByOrderId(orderId));
    }
}
