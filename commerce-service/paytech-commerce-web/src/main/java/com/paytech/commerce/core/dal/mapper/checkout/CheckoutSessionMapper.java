package com.paytech.commerce.core.dal.mapper.checkout;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.customer.CheckoutVersionView;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;

/** Checkout Session 写入与 CAS 映射。 */
public interface CheckoutSessionMapper {
    /** 按原 Token 仅解析不可变的订单主键。 */
    Long findOrderIdByPublicToken(@Param("token") String token);

    /** 按大小写敏感的原 Token 读取会话。 */
    CheckoutSession findByPublicToken(@Param("token") String token);

    /** 同一 SQL 重验原会话绑定与三项本地版本。 */
    CheckoutVersionView findQueryVersions(@Param("token") String token, @Param("orderId") long orderId,
        @Param("sessionId") long sessionId);

    /** 只读取订单实际关联的会话。 */
    CheckoutSession findByOrderId(@Param("orderId") long orderId);

    int insert(CheckoutSession session);

    /** 与 Order 取消共用事务，以会话版本和原状态关闭 Session。 */
    int cancel(@Param("session") CheckoutSession session, @Param("now") Instant now);

    int expire(@Param("orderId") long orderId, @Param("clientId") long clientId,
        @Param("orderNo") String orderNo, @Param("expectedStatus") CheckoutSessionStatus expectedStatus,
        @Param("expectedVersion") long expectedVersion, @Param("nextStatus") CheckoutSessionStatus nextStatus,
        @Param("updatedAt") java.time.Instant updatedAt);

}
