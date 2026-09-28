package com.paytech.commerce.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.*;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Payment;

/** 订单及退款位置查询的真实前置写入和只读快照，不调用被测查询 Mapper 构造断言。 */
@Mapper
public interface OrderQueryTestMapper {
    /** 插入本例订单，缺失/错误业务事实保留给 HTTP 入口验证。 */
    @Insert("""
        INSERT INTO t_commerce_order (order_id,order_no,client_id,client_order_id,creation_mode,
          consumer_country_code,context_format,entity_no,preparation_command_id,merchandise_amount,
          discount_amount,net_amount,tax_amount,rounding_amount,total_amount,currency,order_status,
          expires_at,paid_at,version,create_time,update_time)
        VALUES (#{orderId},#{orderNo},#{clientId},#{clientOrderId},#{creationMode},#{consumerCountryCode},
          'TERM_PRICE',#{entityNo},#{preparationCommandId},#{merchandiseAmount},#{discountAmount},#{netAmount},
          #{taxAmount},#{roundingAmount},#{totalAmount},#{currency},#{orderStatus},#{expiresAt},#{paidAt},
          #{version},#{createTime},#{updateTime})
        """)
    void insertOrder(CommerceOrder order);

    /** 准备真实关联 Session，公开坐标仍由生产 Converter 处理。 */
    @Insert("""
        INSERT INTO t_checkout_session (checkout_session_id,order_id,public_token,public_origin,
          session_status,version,create_time,update_time)
        VALUES (#{checkoutSessionId},#{orderId},#{publicToken},#{publicOrigin},#{sessionStatus},
          #{version},#{createTime},#{updateTime})
        """)
    void insertSession(CheckoutSession session);

    /** 按调用顺序插入商品，查询必须自行按 sortNo 排序。 */
    @Insert("""
        INSERT INTO t_order_item (order_item_id,order_id,client_item_id,item_name,item_description,
          product_type_code,tax_category_code,quantity,unit_amount,line_gross_amount,line_discount_amount,
          line_net_amount,currency,sort_no,create_time)
        VALUES (#{orderItemId},#{orderId},#{clientItemId},#{itemName},#{itemDescription},#{productTypeCode},
          #{taxCategoryCode},#{quantity},#{unitAmount},#{lineGrossAmount},#{lineDiscountAmount},
          #{lineNetAmount},#{currency},#{sortNo},#{createTime})
        """)
    void insertItem(OrderItem item);

    /** 退款前置金额直接插入，不通过退款业务制造查询场景。 */
    @Insert("""
        INSERT INTO t_order_refund_position (order_refund_position_id,order_id,refundable_base_amount,
          reserved_amount,refunded_amount,currency,version,create_time,update_time)
        VALUES (#{orderRefundPositionId},#{orderId},#{refundableBaseAmount},#{reservedAmount},#{refundedAmount},
          #{currency},#{version},#{createTime},#{updateTime})
        """)
    void insertRefund(OrderRefundPosition refund);

    /** 模拟两次查询之间已提交的退款位置变更，不执行生产退款命令。 */
    @Update("""
        UPDATE t_order_refund_position
        SET refundable_base_amount=#{refundableBaseAmount},reserved_amount=#{reservedAmount},
          refunded_amount=#{refundedAmount},version=#{version},update_time=#{updateTime}
        WHERE order_refund_position_id=#{orderRefundPositionId} AND order_id=#{orderId}
        """)
    int updateRefundPosition(OrderRefundPosition refund);

    /** 资金成熟事实来自 Fixture，不根据系统时间生成可用余额。 */
    @Insert("""
        INSERT INTO t_order_funds_availability_projection (order_funds_availability_projection_id,order_id,
          funds_availability_status,available_amount,currency,scheduled_at,available_at,as_of,
          version,create_time,update_time)
        VALUES (#{orderFundsAvailabilityProjectionId},#{orderId},#{fundsAvailabilityStatus},#{availableAmount},
          #{currency},#{scheduledAt},#{availableAt},#{asOf},#{version},#{createTime},#{updateTime})
        """)
    void insertFunds(OrderFundsAvailabilityProjection funds);

    /** 所有外部身份均为合成隔离值，支付事件字段只供本地投影读取。 */
    @Insert("""
        INSERT INTO t_order_payment_projection (order_payment_projection_id,order_id,payment_intent_id,
          intent_status,intent_source_version,last_attempt_id,last_attempt_status,successful_attempt_id,
          captured_amount,currency,last_event_time,version,create_time,update_time)
        VALUES (#{orderId},#{orderId},#{orderId},#{payment.intentStatus},1,#{payment.lastAttemptId},
          #{payment.lastAttemptStatus},#{payment.successfulAttemptId},#{payment.capturedAmount},#{payment.currency},
          #{payment.updateTime},11,#{payment.updateTime},#{payment.updateTime})
        """)
    void insertPayment(@Param("orderId") long orderId, @Param("payment") Payment payment);

    /** 插入已冻结商业上下文，验证查询使用冻结国家而不是重新请求 CCT。 */
    @Insert("""
        INSERT INTO t_order_context (order_context_id,order_id,commercial_context_time,affiliation_id,
          entity_scope_id,entity_source_relation_id,client_pricing_version_id,risk_checkout_assessment_id,
          finance_tax_snapshot_id,finance_tax_snapshot_hash,consumer_country_code,locale,create_time)
        VALUES (#{order.orderId},#{order.orderId},#{order.createTime},101,102,103,104,105,106,
          REPEAT('a',64),#{contextCountry},'en-US',#{order.createTime})
        """)
    void insertContext(Fixture fixture);

    /** 订单全部持久化列，用于前后快照比较，包含内部版本和不可见字段。 */
    @Select("SELECT * FROM t_commerce_order WHERE order_id=#{orderId}")
    List<Map<String, Object>> orderRows(long orderId);

    /** Session 全列快照能发现刷新 Token、到期时间或推进状态的错误副作用。 */
    @Select("SELECT * FROM t_checkout_session WHERE order_id=#{orderId}")
    List<Map<String, Object>> sessionRows(long orderId);

    /** 商品快照按稳定主键排序，不复用被测展示排序 SQL。 */
    @Select("SELECT * FROM t_order_item WHERE order_id=#{orderId} ORDER BY order_item_id")
    List<Map<String, Object>> itemRows(long orderId);

    /** 支付全列快照检查 GET 没有改变支付事实。 */
    @Select("SELECT * FROM t_order_payment_projection WHERE order_id=#{orderId}")
    List<Map<String, Object>> paymentRows(long orderId);

    /** 退款全列快照检查 GET 不预占或消耗金额。 */
    @Select("SELECT * FROM t_order_refund_position WHERE order_id=#{orderId}")
    List<Map<String, Object>> refundRows(long orderId);

    /** 资金全列快照检查 GET 不按当前时间宣布资金成熟。 */
    @Select("SELECT * FROM t_order_funds_availability_projection WHERE order_id=#{orderId}")
    List<Map<String, Object>> fundsRows(long orderId);

    /** 商业上下文不可被查询重新解析或覆盖。 */
    @Select("SELECT * FROM t_order_context WHERE order_id=#{orderId}")
    List<Map<String, Object>> contextRows(long orderId);
}
