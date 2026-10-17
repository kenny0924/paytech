package com.paytech.commerce.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.*;

import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;

import lombok.Getter;
import lombok.Setter;

/** 退款测试只操作自有订单；断言查询独立于被测 Repository。 */
@Mapper
public interface RefundCreationTestMapper {
    @Select("SELECT * FROM t_refund_request WHERE order_id=#{orderId} ORDER BY refund_request_id")
    @Results({@Result(column = "refund_request_id", property = "refundRequestId"),
        @Result(column = "refund_request_no", property = "refundRequestNo"),
        @Result(column = "order_id", property = "orderId"), @Result(column = "client_id", property = "clientId"),
        @Result(column = "request_origin", property = "requestOrigin"),
        @Result(column = "client_refund_id", property = "clientRefundId"),
        @Result(column = "refund_amount", property = "refundAmount"),
        @Result(column = "reason_code", property = "reasonCode"),
        @Result(column = "reason_summary", property = "reasonSummary"),
        @Result(column = "refund_status", property = "refundStatus"),
        @Result(column = "status_reason_code", property = "statusReasonCode"),
        @Result(column = "channel_refund_id", property = "channelRefundId"),
        @Result(column = "completed_at", property = "completedAt"),
        @Result(column = "create_time", property = "createTime"),
        @Result(column = "update_time", property = "updateTime")})
    List<RefundRequest> refunds(long orderId);

    @Select("SELECT * FROM t_order_refund_position WHERE order_id=#{orderId}")
    @Results({@Result(column = "order_refund_position_id", property = "orderRefundPositionId"),
        @Result(column = "order_id", property = "orderId"),
        @Result(column = "refundable_base_amount", property = "refundableBaseAmount"),
        @Result(column = "reserved_amount", property = "reservedAmount"),
        @Result(column = "refunded_amount", property = "refundedAmount"),
        @Result(column = "create_time", property = "createTime"),
        @Result(column = "update_time", property = "updateTime")})
    OrderRefundPosition position(long orderId);

    @Select("""
        SELECT e.* FROM t_compensation_event e JOIN t_refund_request r ON r.refund_request_no=e.event_key
        WHERE r.order_id=#{orderId} AND e.event_type='SUBMISSION_EVENT_TYPE' AND e.sub_event_type='-'
        ORDER BY e.id
        """)
    @Results(id = "task", value = {@Result(column = "event_key", property = "eventKey"),
        @Result(column = "event_type", property = "eventType"),
        @Result(column = "sub_event_type", property = "subEventType"),
        @Result(column = "payload_json", property = "payloadJson"),
        @Result(column = "execute_status", property = "executeStatus"),
        @Result(column = "execute_count", property = "executeCount"),
        @Result(column = "next_execute_at", property = "nextExecuteAt"),
        @Result(column = "lock_owner", property = "lockOwner"),
        @Result(column = "lock_until", property = "lockUntil"),
        @Result(column = "last_error_code", property = "lastErrorCode"),
        @Result(column = "last_error_summary", property = "lastErrorSummary"),
        @Result(column = "lock_version", property = "lockVersion"),
        @Result(column = "create_time", property = "createTime"),
        @Result(column = "update_time", property = "updateTime")})
    List<Task> tasks(long orderId);

    /** 回滚测试中的事件尚未关联已提交退款，也需按精确业务身份确认不存在。 */
    @Select("""
        SELECT * FROM t_compensation_event
        WHERE event_key=#{refundNo} AND event_type='SUBMISSION_EVENT_TYPE' AND sub_event_type='-'
        """)
    @ResultMap("task")
    Task task(String refundNo);

    /** 快速报告手工建表前置，而不是等待异步任务才发现缺少表。 */
    @Select("""
        SELECT r.refund_request_id,e.lock_version FROM t_refund_request r
        LEFT JOIN t_compensation_event e ON e.event_key=r.refund_request_no LIMIT 0
        """)
    void checkRefundTables();

    @Insert("""
        INSERT INTO t_refund_request (refund_request_id,refund_request_no,order_id,client_id,request_origin,
          client_refund_id,refund_amount,currency,reason_code,reason_summary,refund_status,status_reason_code,
          channel_refund_id,completed_at,version,create_time,update_time)
        VALUES (#{refundRequestId},#{refundRequestNo},#{orderId},#{clientId},#{requestOrigin},#{clientRefundId},
          #{refundAmount},#{currency},#{reasonCode},#{reasonSummary},#{refundStatus},#{statusReasonCode},
          #{channelRefundId},#{completedAt},#{version},#{createTime},#{updateTime})
        """)
    void insertRefund(RefundRequest refund);

    /** 只读快照保留全列，用于核对 GET 没有改变状态、版本、预占或提交任务。 */
    @Select("SELECT * FROM t_refund_request WHERE order_id=#{orderId} ORDER BY refund_request_id")
    List<Map<String, Object>> refundRows(long orderId);

    /** 包含关联订单缺失的退款，避免只读检查遗漏孤立前置事实。 */
    @Select("SELECT DISTINCT order_id FROM t_refund_request WHERE client_id IN (#{clientId},#{otherClientId}) ORDER BY order_id")
    List<Long> refundOrderIds(@Param("clientId") long clientId, @Param("otherClientId") long otherClientId);

    @Select("""
        SELECT e.* FROM t_compensation_event e JOIN t_refund_request r ON r.refund_request_no=e.event_key
        WHERE r.order_id=#{orderId} ORDER BY e.id
        """)
    List<Map<String, Object>> taskRows(long orderId);

    /** 固定未决任务，GET 不应尝试执行或推进它。 */
    @Insert("""
        INSERT INTO t_compensation_event (id,event_key,event_type,sub_event_type,execute_status,
          execute_count,next_execute_at,lock_version,create_time,update_time)
        VALUES (#{id},#{refund.refundRequestNo},'SUBMISSION_EVENT_TYPE','-','INIT',0,
          #{refund.updateTime},1,#{refund.createTime},#{refund.updateTime})
        """)
    void insertTask(@Param("id") long id, @Param("refund") RefundRequest refund);

    /** 测试侧状态投影变更；保留分页锚点使用的不变字段。 */
    @Update("""
        UPDATE t_refund_request SET refund_status=#{refundStatus},status_reason_code=#{statusReasonCode},
          channel_refund_id=#{channelRefundId},completed_at=#{completedAt},version=#{version},update_time=#{updateTime}
        WHERE refund_request_id=#{refundRequestId} AND order_id=#{orderId} AND client_id=#{clientId}
        """)
    int updateState(RefundRequest refund);

    @Delete("""
        DELETE e FROM t_compensation_event e JOIN t_refund_request r ON r.refund_request_no=e.event_key
        WHERE r.order_id=#{orderId} AND e.event_type='SUBMISSION_EVENT_TYPE' AND e.sub_event_type='-'
        """)
    int deleteTasks(long orderId);

    @Delete("DELETE FROM t_refund_request WHERE order_id=#{orderId}")
    int deleteRequests(long orderId);

    /** 前置数据模拟另一笔写入已改变版本，直接验证真实 CAS 拒绝过期 Context。 */
    @Update("UPDATE t_order_refund_position SET version=version+1 WHERE order_id=#{orderId}")
    int advancePositionVersion(long orderId);

    /** 只读补偿任务快照；不调用 Dispatcher 或推进业务来满足断言。 */
    @Getter
    @Setter
    class Task {
        private long id;
        private String eventKey;
        private String eventType;
        private String subEventType;
        private String payloadJson;
        private String executeStatus;
        private int executeCount;
        private Instant nextExecuteAt;
        private String lockOwner;
        private Instant lockUntil;
        private String lastErrorCode;
        private String lastErrorSummary;
        private long lockVersion;
        private Instant createTime;
        private Instant updateTime;
    }
}
