package com.paytech.commerce.domainservice.repository.order;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.commerce.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.commerce.core.dal.mapper.order.OrderFundsAvailabilityProjectionMapper;
import com.paytech.foundation.common.error.ConcurrentRequestException;
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

    /** 投影缺失或已不是 NOT_APPLICABLE 时抛出并发冲突，由调用方事务整体回滚。 */
    @Override
    public void markPending(long orderId, Instant asOf, Instant now) {
        if (mapper.markPending(orderId, asOf, now) != 1) {
            throw new ConcurrentRequestException("支付成功时资金可用性投影已改变");
        }
    }
}
