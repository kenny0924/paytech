package com.paytech.commerce.domainservice.repository.order;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.commerce.core.dal.mapper.order.OrderFundsAvailabilityProjectionMapper;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** OrderFundsAvailabilityProjection 的 MyBatis 持久化。 */
@Repository
@AllArgsConstructor
public class OrderFundsAvailabilityProjectionRepositoryImpl implements OrderFundsAvailabilityProjectionRepository {
    private final OrderFundsAvailabilityProjectionMapper mapper;

    /** 插入后核对行数，失败由调用方事务整体回滚。 */
    @Override
    public void insert(OrderFundsAvailabilityProjection value) {
        if (mapper.insert(value) != 1) {
            throw new ErrorException("插入 OrderFundsAvailabilityProjection 受影响行数异常");
        }
    }
}
