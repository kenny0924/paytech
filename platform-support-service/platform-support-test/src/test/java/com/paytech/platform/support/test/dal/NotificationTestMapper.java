package com.paytech.platform.support.test.dal;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 测试专用 Fixture 和只读检查；只清理 NOTIFICATION_TEST 及保留测试配置身份。 */
public interface NotificationTestMapper {

    /** 检查新增表已手工准备。 */
    @Select("SELECT COUNT(*) FROM t_notification_request WHERE source_system='NOTIFICATION_TEST'")
    int requests();

    /** 扫描器按类型领取任务，独占测试开始前不得已有其他通知任务。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY'")
    int notificationTaskCount();

    /** Fixture 身份必须未被占用，避免失败后的清理删除原有配置。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_notification_template WHERE template_id=890001 OR template_code='NOTIFICATION_TEST')
             + (SELECT COUNT(*) FROM t_notification_template_content WHERE content_id IN (890002,890003) OR template_id=890001)
             + (SELECT COUNT(*) FROM t_notification_provider WHERE provider_id IN (890004,890006) OR provider_code IN ('NOTIFICATION_TEST_EMAIL','NOTIFICATION_TEST_FALLBACK'))
             + (SELECT COUNT(*) FROM t_notification_route WHERE route_id=890005 OR notification_template_id=890001)
        """)
    int reservedConfigurationCount();

    /** 插入本例邮件模板。 */
    @Insert("INSERT INTO t_notification_template VALUES(890001,'NOTIFICATION_TEST','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void template();

    /** 插入两种当前语言内容。 */
    @Insert("""
        INSERT INTO t_notification_template_content VALUES
        (890002,890001,'zh-CN',#{subjectZh},#{bodyZh},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
        (890003,890001,'en-US',#{subjectEn},#{bodyEn},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void contents(@Param("subjectZh") String subjectZh, @Param("bodyZh") String bodyZh,
        @Param("subjectEn") String subjectEn, @Param("bodyEn") String bodyEn);

    /** 插入当前测试账号和路由。 */
    @Insert("INSERT INTO t_notification_provider VALUES(890004,'NOTIFICATION_TEST_EMAIL','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void provider();

    /** 路由仅指向测试账号，没有真实外发。 */
    @Insert("INSERT INTO t_notification_route VALUES(890005,890001,890004,NULL,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void route();

    /** 当前用例额外配置一个不同请求号协议的备用账号。 */
    @Insert("INSERT INTO t_notification_provider VALUES(890006,'NOTIFICATION_TEST_FALLBACK','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void fallbackProvider();

    /** 模拟管理员设置主备路由。 */
    @Update("UPDATE t_notification_route SET fallback_provider_id=890006,version=version+1 WHERE route_id=890005")
    void useFallback();

    /** 读取当前用例的 Delivery 状态，不输出 Token 或参数。 */
    @Select("""
        SELECT d.delivery_id,d.status,d.attempt_count,d.version,d.next_retry_time FROM t_notification_delivery d
        JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST'
        ORDER BY d.delivery_id
        """)
    List<Map<String,Object>> deliveries();

    /** 读取发送身份与结果。 */
    @Select("""
        SELECT a.attempt_id,a.delivery_id,a.provider_id,a.provider_request_no,a.status,a.resolution_source
        FROM t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id
        JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST'
        ORDER BY a.attempt_id
        """)
    List<Map<String,Object>> attempts();

    /** 等待本例 afterCommit 异步完全落到可扫描或终态。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST') AND execute_status IN ('INIT','PROCESSING')")
    int activeTasks();

    /** 有恢复事实但不得携带 Payload。 */
    @Select("SELECT event_key,payload_json,execute_status,execute_count FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST')")
    List<Map<String,Object>> events();

    /** 测试时使本例等待任务立即到期；执行仍经过正式 Scanner。 */
    @Update("UPDATE t_compensation_event SET next_execute_at=UTC_TIMESTAMP(6) WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST') AND execute_status='ERROR'")
    void due();

    /** 模拟已到业务重试时间，不调整宿主机器时钟。 */
    @Update("UPDATE t_notification_delivery SET next_retry_time=#{now} WHERE request_id IN (SELECT request_id FROM t_notification_request WHERE source_system='NOTIFICATION_TEST') AND status='RETRY_WAIT'")
    void retryDue(@Param("now") java.time.Instant now);

    /** 模拟管理员更新当前文案。 */
    @Update("UPDATE t_notification_template_content SET body_template=#{body},version=version+1 WHERE content_id=890002")
    void changeContent(@Param("body") String body);

    /** 停用当前账号仍允许查询历史尝试。 */
    @Update("UPDATE t_notification_provider SET status='DISABLED',version=version+1 WHERE provider_id=890004")
    void disableProvider();

    /** 只删除本例持久化任务，先确保异步执行已经停止。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST')")
    void clearEvents();

    /** 按业务来源清理本例尝试。 */
    @Delete("DELETE a FROM t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST'")
    void clearAttempts();

    /** 按业务来源清理本例投递。 */
    @Delete("DELETE d FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system='NOTIFICATION_TEST'")
    void clearDeliveries();

    /** 不清理其他调用方数据。 */
    @Delete("DELETE FROM t_notification_request WHERE source_system='NOTIFICATION_TEST'")
    void clearRequests();

    /** 清理本例路由。 */
    @Delete("DELETE FROM t_notification_route WHERE route_id=890005")
    void clearRoute();

    /** 清理本例账号。 */
    @Delete("DELETE FROM t_notification_provider WHERE provider_id IN (890004,890006)")
    void clearProvider();

    /** 清理本例语言内容。 */
    @Delete("DELETE FROM t_notification_template_content WHERE content_id IN (890002,890003)")
    void clearContents();

    /** 清理本例模板。 */
    @Delete("DELETE FROM t_notification_template WHERE template_id=890001")
    void clearTemplate();
}
