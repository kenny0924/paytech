package com.paytech.commerce.domainservice.repository.order;

import com.paytech.commerce.core.dal.dataobject.order.OrderFundsAvailabilityProjection;

/** OrderFundsAvailabilityProjection 的单表持久化边界。 */
public interface OrderFundsAvailabilityProjectionRepository {
    /** 插入一条记录，参与调用方本地事务。 */
    void insert(OrderFundsAvailabilityProjection value);
}
