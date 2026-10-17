package com.paytech.acquiring.domainservice.repository.order;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.mapper.order.OrderItemMapper;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** OrderItem 的 MyBatis 持久化。 */
@Repository
@AllArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {
    private final OrderItemMapper mapper;

    /** 查询本表商品事实，不再重复联查已授权的订单。 */
    @Override
    public List<OrderItem> findByOrderId(long orderId) {
        return mapper.findByOrderId(orderId);
    }

    /** 插入后核对行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(OrderItem value) {
        if (mapper.insert(value) != 1) {
            throw new ErrorException("插入 OrderItem 受影响行数异常");
        }
    }
}
