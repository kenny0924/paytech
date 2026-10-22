package com.paytech.acquiring.test.dal;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 过期测试的到期时刻准备与订单原因码读取；不调用生产业务命令。 */
@Mapper
public interface OrderExpiryTestMapper {
    /** 把已插入订单的到期时刻改为指定值，制造到期或未到期事实。 */
    @Update("UPDATE t_commerce_order SET expires_at=#{expiresAt} WHERE order_id=#{orderId}")
    int updateExpiresAt(@Param("orderId") long orderId, @Param("expiresAt") Instant expiresAt);

    /** 订单对象不映射原因码，单独读取。 */
    @Select("SELECT status_reason_code FROM t_commerce_order WHERE order_id=#{orderId}")
    String statusReasonCode(long orderId);
}
