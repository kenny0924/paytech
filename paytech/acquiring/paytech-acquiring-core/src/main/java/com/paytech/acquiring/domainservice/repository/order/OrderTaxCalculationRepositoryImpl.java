package com.paytech.acquiring.domainservice.repository.order;

import org.springframework.stereotype.Repository;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.core.dal.mapper.order.OrderTaxCalculationMapper;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** OrderTaxCalculation 的 MyBatis 持久化。 */
@Repository
@AllArgsConstructor
public class OrderTaxCalculationRepositoryImpl implements OrderTaxCalculationRepository {
    private final OrderTaxCalculationMapper mapper;

    /** 插入后核对行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(OrderTaxCalculation row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("插入 OrderTaxCalculation 受影响行数异常");
        }
    }

    /** 按 revision 倒序取第一行。 */
    @Override
    public OrderTaxCalculation findLatestByOrderId(long orderId) {
        return mapper.findLatestByOrderId(orderId);
    }
}
