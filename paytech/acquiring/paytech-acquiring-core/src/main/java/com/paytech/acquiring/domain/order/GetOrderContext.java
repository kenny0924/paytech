package com.paytech.commerce.domain.order;

import java.util.List;

import com.paytech.commerce.core.dal.dataobject.order.*;

/** 订单查询上下文；持有本模块数据库对象，不传入 Controller 或 Repository。 */
public record GetOrderContext(OrderRecord order, List<OrderItem> items, OrderPaymentRecord payment,
    OrderRefundPosition refund, OrderFundsRecord funds, String consumerCountryCode) {
}
