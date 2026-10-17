package com.paytech.commerce.test.dal;

import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Client Webhook 补偿任务的只读检查、损坏载荷与缺失载荷 Fixture，以及按订单清理；订单与退款由 CheckoutDataMocker 统一清理。 */
@Mapper
public interface ClientWebhookTestMapper {
    /** 事件对应的补偿任务；成功后删除，返回 null。 */
    @Select("""
        SELECT event_key,event_type,sub_event_type,payload_json,execute_status,execute_count,last_error_code,
          last_error_summary,lock_owner
        FROM t_compensation_event WHERE event_key=#{eventKey} AND event_type='CLIENT_WEBHOOK_REQUESTED'
        """)
    Map<String, Object> task(String eventKey);

    /** 改写冻结载荷，模拟数据库中内容被篡改。 */
    @Update("""
        UPDATE t_compensation_event SET payload_json=#{payloadJson}
        WHERE event_key=#{eventKey} AND event_type='CLIENT_WEBHOOK_REQUESTED'
        """)
    int replacePayload(@Param("eventKey") String eventKey, @Param("payloadJson") String payloadJson);

    /** 人工插入的无载荷任务，验证 Handler 拒绝不符合登记合同的元数据。 */
    @Insert("""
        INSERT INTO t_compensation_event (id,event_key,event_type,sub_event_type,execute_status,
          execute_count,next_execute_at,lock_version,create_time,update_time)
        VALUES (#{id},#{eventKey},'CLIENT_WEBHOOK_REQUESTED',#{subEventType},'INIT',0,
          UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void insertTaskWithoutPayload(@Param("id") long id, @Param("eventKey") String eventKey,
        @Param("subEventType") String subEventType);

    /** 本测试订单可能修改后的版本，用于证明补交内容不按最新数据重建。 */
    @Update("UPDATE t_commerce_order SET client_order_id=#{clientOrderId},version=version+1 WHERE order_id=#{orderId}")
    int changeOrder(@Param("orderId") long orderId, @Param("clientOrderId") String clientOrderId);

    /** 删除订单类事件任务（paid / cancelled）。 */
    @Delete("""
        DELETE e FROM t_compensation_event e JOIN t_commerce_order o
          ON e.event_key IN (CONCAT('evt_',o.order_no,'_paid'),CONCAT('evt_',o.order_no,'_cancelled'))
        WHERE o.order_id=#{orderId} AND e.event_type='CLIENT_WEBHOOK_REQUESTED'
        """)
    void deleteOrderTasks(long orderId);

    /** 删除退款类事件任务（created）。 */
    @Delete("""
        DELETE e FROM t_compensation_event e JOIN t_refund_request r ON e.event_key=CONCAT('evt_',r.refund_request_no,'_created')
        WHERE r.order_id=#{orderId} AND e.event_type='CLIENT_WEBHOOK_REQUESTED'
        """)
    void deleteRefundTasks(long orderId);
}
