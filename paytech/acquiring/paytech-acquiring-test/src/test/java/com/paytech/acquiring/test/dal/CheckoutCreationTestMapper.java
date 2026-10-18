package com.paytech.acquiring.test.dal;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.*;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;

/** 本接口测试专用的前置数据、清理和只读查询；不替换生产 Mapper。 */
@Mapper
public interface CheckoutCreationTestMapper {
    /** 零行查询只检查当前写链必需的列存在，绝不创建或迁移 Schema。 */
    @Select("SELECT COUNT(creation_mode)+COUNT(consumer_country_code)+COUNT(customer_id) FROM t_commerce_order WHERE 1=0")
    int checkCreationColumns();

    /** 只枚举本次测试环境拥有的两个 Client 的订单。 */
    @Select("SELECT order_id FROM t_commerce_order WHERE client_id IN (#{clientId},#{otherClientId})")
    List<Long> orderIds(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    /** 查询原始数据库对象，断言不依赖 HTTP 查询接口。 */
    @Select("SELECT * FROM t_commerce_order WHERE client_id=#{clientId} AND client_order_id=#{clientOrderId}")
    @ResultMap("com.paytech.acquiring.core.dal.mapper.order.CommerceOrderMapper.CommerceOrder")
    CommerceOrder order(@Param("clientId") long clientId, @Param("clientOrderId") String clientOrderId);

    /** 保留数据库商品行顺序并读取实际金额。 */
    @Select("SELECT *,NULL AS line_tax_amount,NULL AS line_total_amount FROM t_order_item WHERE order_id=#{orderId} ORDER BY sort_no")
    @ResultMap("com.paytech.acquiring.core.dal.mapper.checkout.BasicCheckoutMapper.Item")
    List<OrderItem> items(long orderId);

    /** 订单上下文冻结的 Risk 结账评估 ID。 */
    @Select("SELECT risk_checkout_assessment_id FROM t_order_context WHERE order_id=#{orderId}")
    Long riskCheckoutAssessmentId(long orderId);

    /** 一笔创建只允许一条 Checkout 会话。 */
    @Select("SELECT * FROM t_checkout_session WHERE order_id=#{orderId}")
    @ResultMap("com.paytech.acquiring.core.dal.mapper.checkout.CheckoutSessionMapper.CheckoutSession")
    CheckoutSession session(long orderId);

    /** 读取真实退款初始位置。 */
    @Select("SELECT * FROM t_order_refund_position WHERE order_id=#{orderId}")
    @ResultMap("com.paytech.acquiring.core.dal.mapper.order.OrderRefundPositionMapper.OrderRefundPosition")
    OrderRefundPosition refund(long orderId);

    /** 检查资金投影初态；单表 EXISTS 同时验证零事实没有被伪造为金额。 */
    @Select("""
        SELECT COUNT(*) FROM t_order_funds_availability_projection WHERE order_id=#{orderId}
        AND funds_availability_status='NOT_APPLICABLE' AND currency='USD' AND version=1
        AND finance_economics_id IS NULL AND maturity_journal_id IS NULL AND available_amount IS NULL
        AND scheduled_at IS NULL AND available_at IS NULL AND as_of=#{now}
        AND create_time=#{now} AND update_time=#{now}
        """)
    int initialFunds(@Param("orderId") long orderId, @Param("now") Instant now);

    /** 基础创建不能生成支付、准备或法律事实。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_order_legal l JOIN t_order_context c ON c.order_context_id=l.order_context_id WHERE c.order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_context WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_consumer_contract WHERE order_id=#{orderId})
        """)
    int preparedFacts(long orderId);

    /** 按资源身份检查创建及查询 Fixture，也能发现父订单不存在时的残留子行。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_commerce_order WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_item WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_checkout_session WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_refund_position WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_funds_availability_projection WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_payment_projection WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_context WHERE order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_order_legal l JOIN t_order_context c ON c.order_context_id=l.order_context_id WHERE c.order_id=#{orderId})
             + (SELECT COUNT(*) FROM t_consumer_contract WHERE order_id=#{orderId})
        """)
    int creationRows(long orderId);

    /** 已存在业务编号前置事实，不通过调用被测接口完成 Arrange。 */
    @Insert("""
        INSERT INTO t_commerce_order (order_id,order_no,client_id,client_order_id,creation_mode,consumer_country_code,
          merchandise_amount,discount_amount,net_amount,currency,order_status,expires_at,version,create_time,update_time)
        VALUES (#{orderId},#{orderNo},#{clientId},#{clientOrderId},'HOSTED_CHECKOUT','US',
          44.90,2.00,42.90,'USD',#{status},#{expiresAt},1,#{now},#{now})
        """)
    void insertOrder(@Param("orderId") long orderId, @Param("orderNo") String orderNo, @Param("clientId") long clientId,
        @Param("clientOrderId") String clientOrderId, @Param("status") String status,
        @Param("expiresAt") Instant expiresAt, @Param("now") Instant now);

    /** 插入占用 Public Token 的已存在会话，用真实唯一约束触发末步失败。 */
    @Insert("""
        INSERT INTO t_checkout_session (checkout_session_id,order_id,public_token,public_origin,
          session_status,version,create_time,update_time)
        VALUES (#{orderId},#{orderId},#{token},'https://checkout.local.test','PREPARING',1,#{now},#{now})
        """)
    void insertSession(@Param("orderId") long orderId, @Param("token") String token, @Param("now") Instant now);

    /** 清理范围由已枚举的本测试订单 ID 限定。 */
    @Delete("DELETE FROM t_order_item WHERE order_id=#{orderId}")
    void deleteItems(long orderId);

    /** 清理查询场景插入的本测试支付投影。 */
    @Delete("DELETE FROM t_order_payment_projection WHERE order_id=#{orderId}")
    void deletePayment(long orderId);

    /** 删除属于本测试订单的法律接受、合同、法律事实及消费者。 */
    @Delete("DELETE a FROM t_consumer_acceptance a JOIN t_consumer_contract c ON c.consumer_contract_id=a.consumer_contract_id WHERE c.order_id=#{orderId}")
    void deleteAcceptances(long orderId);
    @Delete("DELETE FROM t_checkout_network_evidence WHERE order_id=#{orderId}")
    void deleteEvidence(long orderId);
    @Delete("DELETE FROM t_consumer_context WHERE order_id=#{orderId}")
    void deleteConsumer(long orderId);
    @Delete("DELETE FROM t_consumer_contract WHERE order_id=#{orderId}")
    void deleteContract(long orderId);
    @Delete("DELETE l FROM t_order_legal l JOIN t_order_context c ON c.order_context_id=l.order_context_id WHERE c.order_id=#{orderId}")
    void deleteLegal(long orderId);

    /** 清理查询场景插入的本测试冻结上下文。 */
    @Delete("DELETE FROM t_order_context WHERE order_id=#{orderId}")
    void deleteContext(long orderId);

    /** 清理本测试的 Session。 */
    @Delete("DELETE FROM t_checkout_session WHERE order_id=#{orderId}")
    void deleteSession(long orderId);

    /** 清理本测试的退款初始位置。 */
    @Delete("DELETE FROM t_order_refund_position WHERE order_id=#{orderId}")
    void deleteRefund(long orderId);

    /** 清理本测试的资金初始投影。 */
    @Delete("DELETE FROM t_order_funds_availability_projection WHERE order_id=#{orderId}")
    void deleteFunds(long orderId);

    /** 最后清理根订单，前面的删除和根删除在同一清理事务中。 */
    @Delete("DELETE FROM t_commerce_order WHERE order_id=#{orderId}")
    void deleteOrder(long orderId);

    /** 订单关联的 Customer 主键（consumer 软引用，只存 ID）。 */
    @Select("SELECT customer_id FROM t_commerce_order WHERE client_id=#{clientId} AND client_order_id=#{clientOrderId}")
    Long orderCustomerId(@Param("clientId") long clientId, @Param("clientOrderId") String clientOrderId);

    /** 把订单关联到 consumer 的 Customer 主键。 */
    @Update("UPDATE t_commerce_order SET customer_id=#{customerId} WHERE order_id=#{orderId}")
    int linkOrderCustomer(@Param("orderId") long orderId, @Param("customerId") long customerId);
}
