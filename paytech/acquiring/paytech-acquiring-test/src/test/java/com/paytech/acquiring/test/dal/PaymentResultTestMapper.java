package com.paytech.acquiring.test.dal;

import java.time.Instant;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 支付结果查询与成功事件场景的 Fixture 与只读检查；订单数据由 CheckoutDataMocker 统一清理。 */
@Mapper
public interface PaymentResultTestMapper {
    /** 订单已绑定 Intent、尚无支付结果的投影。 */
    @Insert("""
        INSERT INTO t_order_payment_projection (order_payment_projection_id,order_id,payment_intent_id,intent_status,
          intent_source_version,captured_amount,currency,last_event_time,version,create_time,update_time)
        VALUES (#{orderId},#{orderId},#{intentId},'REQUIRES_PAYMENT',0,0,'USD',#{now},1,#{now},#{now})
        """)
    void insertPayment(@Param("orderId") long orderId, @Param("intentId") long intentId, @Param("now") Instant now);

    /** 订单与会话已取消（Client 取消已完成）。 */
    @Update("""
        UPDATE t_commerce_order o JOIN t_checkout_session s ON s.order_id=o.order_id
        SET o.order_status='CANCELLED',o.status_reason_code='CLIENT_REQUESTED',o.termination_requested_at=#{now},
          o.cancelled_at=#{now},o.version=o.version+1,s.session_status='CANCELLED',s.cancelled_at=#{now},s.version=s.version+1
        WHERE o.order_id=#{orderId}
        """)
    void cancelOrder(@Param("orderId") long orderId, @Param("now") Instant now);

    /** 订单状态、支付时间（UTC 秒级字符串）与版本。 */
    @Select("SELECT order_status,DATE_FORMAT(paid_at,'%Y-%m-%dT%H:%i:%sZ') AS paid_at,version FROM t_commerce_order WHERE order_id=#{orderId}")
    Map<String, Object> order(long orderId);

    /** 会话状态、完成时间与版本。 */
    @Select("SELECT session_status,DATE_FORMAT(completed_at,'%Y-%m-%dT%H:%i:%sZ') AS completed_at,version FROM t_checkout_session WHERE order_id=#{orderId}")
    Map<String, Object> session(long orderId);

    /** 支付投影全部业务列。 */
    @Select("""
        SELECT intent_status,last_attempt_id,last_attempt_status,successful_attempt_id,captured_amount,
          attempt_source_version,last_intent_event_key,last_intent_payload_hash,last_attempt_event_key,
          last_attempt_payload_hash,DATE_FORMAT(last_event_time,'%Y-%m-%dT%H:%i:%sZ') AS last_event_time,version
        FROM t_order_payment_projection WHERE order_id=#{orderId}
        """)
    Map<String, Object> payment(long orderId);

    /** 退款金额位置。 */
    @Select("SELECT refundable_base_amount,reserved_amount,refunded_amount,version FROM t_order_refund_position WHERE order_id=#{orderId}")
    Map<String, Object> refund(long orderId);

    /** 资金可用性投影。 */
    @Select("SELECT funds_availability_status,DATE_FORMAT(as_of,'%Y-%m-%dT%H:%i:%sZ') AS as_of,version FROM t_order_funds_availability_projection WHERE order_id=#{orderId}")
    Map<String, Object> funds(long orderId);
}
