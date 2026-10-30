package com.paytech.acquiring.core.dal.mapper.order;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;

/** 订单商业关系角色绑定表的创建持久化。 */
public interface OrderRelationshipBindingMapper {
    /** 一条语句插入同一订单的全部角色绑定，返回插入行数。 */
    int insertAll(@Param("bindings") List<OrderRelationshipBinding> bindings);

    /** 读取订单的全部角色绑定，按主键排序。 */
    List<OrderRelationshipBinding> findByOrderId(@Param("orderId") long orderId);
}
