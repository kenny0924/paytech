package com.paytech.acquiring.core.dal.mapper.order;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsAvailabilityProjection;

/** OrderFundsAvailabilityProjection 表的创建持久化。 */
public interface OrderFundsAvailabilityProjectionMapper {
    /** 插入已构造的本表记录。 */
    int insert(OrderFundsAvailabilityProjection value);

    /** 支付成功时将不适用的资金投影推进为待成熟，as_of 不回退。 */
    int markPending(@Param("orderId") long orderId, @Param("asOf") Instant asOf, @Param("now") Instant now);
}
