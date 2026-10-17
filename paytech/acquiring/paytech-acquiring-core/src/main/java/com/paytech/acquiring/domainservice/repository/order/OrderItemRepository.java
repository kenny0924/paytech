package com.paytech.commerce.domainservice.repository.order;

import java.util.List;

import com.paytech.commerce.core.dal.dataobject.order.OrderItem;

/** OrderItem 的单表持久化边界。 */
public interface OrderItemRepository {
    /** 读取已授权订单的原商品行，按原呈现序号排序。 */
    List<OrderItem> findByOrderId(long orderId);

    /** 插入一条记录，参与调用方本地事务。 */
    void insert(OrderItem value);
}
