package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Webhook 测试的数据准备、只读检查与清理
 * <li>五张 Webhook 表由 PlatformDataCleaner 整表回收；本 Mapper 只清理自己插入的租户与 WEBHOOK_DELIVERY 补偿任务</li>
 */
public interface WebhookTestMapper {

    /** 保留的测试租户 ID 段未被占用。 */
    @Select("SELECT COUNT(*) FROM t_access_tenant WHERE tenant_id BETWEEN 880000000000071000 AND 880000000000071999")
    int reservedTenantCount();

    /** 测试开始前不得已有 Webhook 补偿任务。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='WEBHOOK_DELIVERY'")
    int taskCount();

    /** 插入 CLIENT 租户。 */
    @Insert("""
        INSERT INTO t_access_tenant(tenant_id,tenant_type,owner_ref_id,tenant_name,owner_status,security_status,
            version,create_time,update_time)
        VALUES(#{tenantId},'CLIENT',#{clientId},'Webhook synthetic test tenant','ACTIVE','ACTIVE',1,
            UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void tenant(@Param("tenantId") long tenantId, @Param("clientId") long clientId);

    /** 修改测试租户名称，用于名称搜索场景。 */
    @Update("UPDATE t_access_tenant SET tenant_name=#{name} WHERE tenant_id=#{tenantId}")
    void renameTenant(@Param("tenantId") long tenantId, @Param("name") String name);

    /** 删除本例租户。 */
    @Delete("DELETE FROM t_access_tenant WHERE tenant_id BETWEEN 880000000000071000 AND 880000000000071999")
    void clearTenants();

    /** 直接插入端点，用于 Handler 与查询场景的前置数据。 */
    @Insert("""
        INSERT INTO t_webhook_endpoint(endpoint_id,tenant_id,url,event_types,status,version,create_time,update_time)
        VALUES(#{endpointId},#{tenantId},#{url},#{eventTypes},#{status},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void endpoint(@Param("endpointId") long endpointId, @Param("tenantId") long tenantId, @Param("url") String url,
        @Param("eventTypes") String eventTypes, @Param("status") String status);

    /** 直接插入签名密钥。 */
    @Insert("""
        INSERT INTO t_webhook_signing_secret(signing_secret_id,endpoint_id,rotation_no,secret_vault_token,status,
            signing_slot,valid_until,version,create_time,update_time)
        VALUES(#{secretId},#{endpointId},#{rotationNo},#{token},#{status},#{slot},#{validUntil},1,
            UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void secret(@Param("secretId") long secretId, @Param("endpointId") long endpointId,
        @Param("rotationNo") int rotationNo, @Param("token") String token, @Param("status") String status,
        @Param("slot") Integer slot, @Param("validUntil") Instant validUntil);

    /** 全部事件。 */
    @Select("SELECT * FROM t_webhook_event ORDER BY create_time, event_id")
    List<Map<String, Object>> events();

    /** 全部投递，按创建顺序。 */
    @Select("SELECT * FROM t_webhook_delivery ORDER BY create_time, delivery_id")
    List<Map<String, Object>> deliveries();

    /** 一个投递。 */
    @Select("SELECT * FROM t_webhook_delivery WHERE delivery_id=#{deliveryId}")
    Map<String, Object> delivery(@Param("deliveryId") long deliveryId);

    /** 投递的全部尝试。 */
    @Select("SELECT * FROM t_webhook_attempt WHERE delivery_id=#{deliveryId} ORDER BY attempt_no")
    List<Map<String, Object>> attempts(@Param("deliveryId") long deliveryId);

    /** 全部端点。 */
    @Select("SELECT * FROM t_webhook_endpoint ORDER BY create_time, endpoint_id")
    List<Map<String, Object>> endpoints();

    /** 端点的全部密钥。 */
    @Select("SELECT * FROM t_webhook_signing_secret WHERE endpoint_id=#{endpointId} ORDER BY rotation_no")
    List<Map<String, Object>> secrets(@Param("endpointId") long endpointId);

    /** Webhook 补偿任务。 */
    @Select("SELECT event_key,payload_json,execute_status,execute_count,last_error_code FROM t_compensation_event WHERE event_type='WEBHOOK_DELIVERY' ORDER BY id")
    List<Map<String, Object>> tasks();

    /** 仍在等待首次执行或执行中的任务数，用于等待异步执行结束。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='WEBHOOK_DELIVERY' AND execute_status IN ('INIT','PROCESSING')")
    int activeTasks();

    /** 使等待重试的任务立即到期；执行仍经过正式 Scanner。 */
    @Update("UPDATE t_compensation_event SET next_execute_at=UTC_TIMESTAMP(6) WHERE event_type='WEBHOOK_DELIVERY' AND execute_status='ERROR'")
    int due();

    /** 删除 Webhook 补偿任务；先确保异步执行已经停止。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type='WEBHOOK_DELIVERY'")
    void clearTasks();

    /** 模拟上次执行在发送中崩溃：投递停在 PROCESSING 并留下一条 PROCESSING 尝试。 */
    @Update("UPDATE t_webhook_delivery SET status='PROCESSING',attempt_count=attempt_count+1,last_attempt_time=UTC_TIMESTAMP(6),version=version+1 WHERE delivery_id=#{deliveryId}")
    void markProcessing(@Param("deliveryId") long deliveryId);

    /** 遗留的 PROCESSING 尝试。 */
    @Insert("""
        INSERT INTO t_webhook_attempt(attempt_id,delivery_id,attempt_no,request_url,signing_secret_id,grace_secret_id,
            status,started_time,create_time,update_time)
        SELECT #{attemptId},d.delivery_id,d.attempt_count,e.url,s.signing_secret_id,NULL,'PROCESSING',
            UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
        FROM t_webhook_delivery d JOIN t_webhook_endpoint e ON e.endpoint_id=d.endpoint_id
        JOIN t_webhook_signing_secret s ON s.endpoint_id=e.endpoint_id AND s.status='ACTIVE'
        WHERE d.delivery_id=#{deliveryId}
        """)
    void processingAttempt(@Param("attemptId") long attemptId, @Param("deliveryId") long deliveryId);

    /** 篡改已保存的 Envelope，用于完整性校验。 */
    @Update("UPDATE t_webhook_event SET payload=JSON_SET(payload,'$.data.object.tampered','yes')")
    void tamperPayloads();

    /** 端点停用。 */
    @Update("UPDATE t_webhook_endpoint SET status='DISABLED',version=version+1 WHERE endpoint_id=#{endpointId}")
    void disable(@Param("endpointId") long endpointId);
}
