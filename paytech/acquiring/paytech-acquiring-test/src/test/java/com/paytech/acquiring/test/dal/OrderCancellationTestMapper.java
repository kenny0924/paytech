package com.paytech.commerce.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.customer.ConsumerAcceptance;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;

/** 取消测试的历史事实、支付接受和真实并发版本准备；不调用生产业务命令。 */
@Mapper
public interface OrderCancellationTestMapper {
    /** 补充查询 Fixture 原本不插入的取消时刻，不改变状态和版本。 */
    @Update("UPDATE t_commerce_order SET termination_requested_at=#{terminationRequestedAt},cancelled_at=#{cancelledAt} WHERE order_id=#{orderId}")
    void orderCancellationFacts(CommerceOrder order);

    /** 插入历史取消时刻，验证重复取消返回原始事实。 */
    @Update("UPDATE t_checkout_session SET cancelled_at=#{cancelledAt} WHERE checkout_session_id=#{checkoutSessionId}")
    void sessionCancellationFacts(CheckoutSession session);

    /** 独立读取订单表，复用正式数据库对象映射。 */
    @Select("SELECT * FROM t_commerce_order WHERE order_id=#{orderId}")
    @ResultMap("com.paytech.commerce.core.dal.mapper.order.CommerceOrderMapper.CommerceOrder")
    CommerceOrder order(long orderId);

    /** 独立读取实际关联的 Session。 */
    @Select("SELECT * FROM t_checkout_session WHERE order_id=#{orderId}")
    @ResultMap("com.paytech.commerce.core.dal.mapper.checkout.CheckoutSessionMapper.CheckoutSession")
    CheckoutSession session(long orderId);

    /** 模拟另一个本地事务推进会话，取消必须由真实 CAS 拒绝。 */
    @Update("UPDATE t_checkout_session SET version=version+1 WHERE order_id=#{orderId}")
    void advanceSessionVersion(long orderId);

    /** 模拟支付事件更新投影版本，不伪造方法返回值。 */
    @Update("UPDATE t_order_payment_projection SET version=version+1 WHERE order_id=#{orderId}")
    void advancePaymentVersion(long orderId);

    /** 合成网络证据只供法律接受的本地关联，不包含真实个人信息。 */
    @Insert("""
        INSERT INTO t_checkout_network_evidence (network_evidence_id,order_id,checkout_session_id,evidence_scene,
          request_id,client_ip_address,user_agent,device_type,occurred_at,create_time)
        VALUES (#{orderId},#{orderId},#{orderId},'LEGAL_ACCEPTANCE','CancelOrder.Test','192.0.2.1',
          'Test Browser','UNKNOWN',#{now},#{now})
        """)
    void insertEvidence(@Param("orderId") long orderId, @Param("now") Instant now);

    /** 三种接受类型由 Mocker 使用数据库对象 Builder 逐行构造。 */
    @Insert("""
        INSERT INTO t_consumer_acceptance (consumer_acceptance_id,consumer_contract_id,network_evidence_id,
          acceptance_type,accepted_at,create_time)
        VALUES (#{consumerAcceptanceId},#{consumerContractId},#{networkEvidenceId},#{acceptanceType},#{acceptedAt},#{createTime})
        """)
    void insertAcceptance(ConsumerAcceptance acceptance);

    /** 取消不得改写既有接受记录。 */
    @Select("SELECT a.* FROM t_consumer_acceptance a JOIN t_consumer_contract c ON c.consumer_contract_id=a.consumer_contract_id WHERE c.order_id=#{orderId} ORDER BY a.consumer_acceptance_id")
    List<Map<String, Object>> acceptances(long orderId);

    /** 网络证据作为取消前后不可变事实检查。 */
    @Select("SELECT * FROM t_checkout_network_evidence WHERE order_id=#{orderId} ORDER BY network_evidence_id")
    List<Map<String, Object>> evidence(long orderId);
}
