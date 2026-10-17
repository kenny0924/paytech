package com.paytech.commerce.core.dal.mapper.order;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.order.OrderItem;

/** OrderItem 表的查询与创建持久化。 */
public interface OrderItemMapper {
    /** 只读本订单的不可变商品行。 */
    List<OrderItem> findByOrderId(@Param("orderId") long orderId);

    /** 插入已构造的本表记录。 */
    int insert(OrderItem value);
}
