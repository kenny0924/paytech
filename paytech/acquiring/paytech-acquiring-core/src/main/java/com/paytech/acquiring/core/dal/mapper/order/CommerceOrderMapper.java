package com.paytech.acquiring.core.dal.mapper.order;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;

/** CommerceOrder 单表查询、创建和状态更新。 */
public interface CommerceOrderMapper {
    /** 按已授权订单主键读取本表。 */
    CommerceOrder findByOrderId(@Param("orderId") long orderId);

    /** 原子确认本次支付受理前提，失败时不创建接受事实。 */
    int recordPaymentSubmission(@Param("order") CommerceOrder order, @Param("session") CheckoutSession session,
        @Param("contractId") long contractId, @Param("payment") OrderPaymentProjection payment,
        @Param("now") Instant now);

    /** 保存消费者前检查原版本、会话及准备状态，并递增订单版本。 */
    int recordConsumerChange(@Param("order") CommerceOrder order, @Param("session") CheckoutSession session,
        @Param("now") Instant now);

    /** 按 Client 和公开订单号查询订单表。 */
    CommerceOrder findByOrderNo(@Param("clientId") long clientId, @Param("orderNo") String orderNo);

    /** 按可信 Client 与公开编号仅选择订单主键。 */
    Long findOrderIdByOrderNo(@Param("clientId") long clientId, @Param("orderNo") String orderNo);

    /** 按可信 Client 和内部主键仅选择公开订单号。 */
    String findOrderNoByOrderId(@Param("clientId") long clientId, @Param("orderId") long orderId);

    /** 查询 Client 内永久唯一的业务订单号。 */
    Long findOrderId(@Param("clientId") long clientId, @Param("clientOrderId") String clientOrderId);

    /** 仅插入尚未到期的订单。 */
    int insert(@Param("order") CommerceOrder order, @Param("now") Instant now);

    /** 在原版本和允许状态下写入终止意图。 */
    int requestTermination(@Param("order") CommerceOrder order, @Param("now") Instant now);

    /** 以订单版本将 OPEN 订单推进为 PAID。 */
    int markPaid(@Param("order") CommerceOrder order, @Param("paidAt") Instant paidAt, @Param("now") Instant now);

    /** 仅在未支付且已请求终止时确认订单取消。 */
    int cancel(@Param("order") CommerceOrder order, @Param("payment") OrderPaymentProjection payment,
        @Param("localOnly") boolean localOnly,
        @Param("now") Instant now);

    /** 按订单主键游标与分片取一页已到期、仍为 CREATED/OPEN 的订单主键。 */
    List<Long> selectExpiredOrderIds(@Param("now") Instant now, @Param("afterOrderId") long afterOrderId,
        @Param("shardIndex") int shardIndex, @Param("shardTotal") int shardTotal, @Param("limit") int limit);

    /** 仅在已到期、未支付且已请求终止时确认订单过期，支付投影守卫与取消一致。 */
    int expire(@Param("order") CommerceOrder order, @Param("payment") OrderPaymentProjection payment,
        @Param("localOnly") boolean localOnly, @Param("now") Instant now);
}
