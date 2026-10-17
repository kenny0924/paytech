package com.paytech.acquiring.domain.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.CustomerSessionView;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRecord;

/** 已定位的订单与 Session；不隐式加载消费者、合同、商品或创建快照。 */
public record CheckoutAccessContext(CustomerSessionView session, OrderRecord order) {
}
