package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 审计事实的只读检查；写入只来自被测审计链路。 */
public interface AuditTestMapper {

    /** 按请求标识读取审计事件。 */
    @Select("SELECT * FROM t_audit_event WHERE request_id=#{requestId} ORDER BY sequence_no")
    List<Map<String, Object>> eventsByRequest(String requestId);

    /** 读取事件属性。 */
    @Select("SELECT * FROM t_audit_event_attr WHERE audit_event_id=#{eventId} ORDER BY attr_no")
    List<Map<String, Object>> attributes(@Param("eventId") long eventId);

    /** 直接插入一条审计事件，供查询入口测试准备不同动作、结果与时间。 */
    @Insert("""
        INSERT INTO t_audit_event(audit_event_id,chain_id,sequence_no,previous_event_hash,canonical_version,
            attribute_root_hash,event_hash,schema_version,actor_type,actor_id,tenant_id,action_code,resource_system,
            resource_type,resource_id,result,request_id,trace_id,occurred_time,received_time,payload_hash,create_time)
        VALUES(#{e.id},#{e.chainId},#{e.sequence},NULL,1,#{e.hash},#{e.hash},1,'USER',#{e.actorId},NULL,#{e.action},
            #{e.system},#{e.resourceType},#{e.resourceId},#{e.result},#{e.requestId},#{e.requestId},#{e.occurred},
            #{e.occurred},#{e.hash},#{e.occurred})
        """)
    int insertEvent(@Param("e") Event event);

    /** 插入审计属性；分级决定查询可见的值。 */
    @Insert("""
        INSERT INTO t_audit_event_attr(attr_id,audit_event_id,attr_no,attr_key,value_text,value_vault_token,
            value_masked,value_hash,classification,create_time)
        VALUES(#{id},#{eventId},#{number},#{key},#{text},#{token},#{masked},#{hash},#{classification},#{now})
        """)
    int insertAttribute(@Param("id") long id, @Param("eventId") long eventId, @Param("number") int number,
        @Param("key") String key, @Param("text") String text, @Param("token") String token,
        @Param("masked") String masked, @Param("hash") String hash, @Param("classification") String classification,
        @Param("now") Instant now);

    /** 全部审计事件数量。 */
    @Select("SELECT COUNT(*) FROM t_audit_event")
    long eventCount();

    /** 审计事件测试参数。 */
    record Event(long id, long chainId, int sequence, String hash, String actorId, String action, String system,
        String resourceType, String resourceId, String result, String requestId, Instant occurred) {
    }
}
