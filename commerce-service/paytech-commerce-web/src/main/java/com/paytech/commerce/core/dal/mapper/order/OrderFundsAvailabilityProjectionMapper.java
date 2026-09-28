package com.paytech.commerce.core.dal.mapper.order;

import com.paytech.commerce.core.dal.dataobject.order.OrderFundsAvailabilityProjection;

/** OrderFundsAvailabilityProjection 表的创建持久化。 */
public interface OrderFundsAvailabilityProjectionMapper {
    /** 插入已构造的本表记录。 */
    int insert(OrderFundsAvailabilityProjection value);
}
