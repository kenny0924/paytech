package com.paytech.platform.support.test.dal;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** 测试专用 Fixture 和只读检查；只清理 notification-test 前缀来源及保留测试配置身份（含认证即时发送模板）。 */
public interface NotificationTestMapper {

    /** 检查新增表已手工准备。 */
    @Select("SELECT COUNT(*) FROM t_notification_request WHERE source_system LIKE 'notification-test%'")
    int requests();

    /** 已受理请求的来源系统与租户，按来源排序。 */
    @Select("SELECT source_system, source_event_key, tenant_id FROM t_notification_request WHERE source_system LIKE 'notification-test%' ORDER BY source_system")
    List<Map<String, Object>> requestSources();

    /** 扫描器按类型领取任务，独占测试开始前不得已有其他通知任务。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY'")
    int notificationTaskCount();

    /** Fixture 身份必须未被占用，避免失败后的清理删除原有配置。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_notification_template WHERE template_id IN (890001,890011,890012)
                    OR template_code IN ('NOTIFICATION_TEST','CLIENT_EMAIL_VERIFICATION','CLIENT_PASSWORD_RESET'))
             + (SELECT COUNT(*) FROM t_notification_template_content WHERE content_id IN (890002,890003,890013,890014)
                    OR template_id IN (890001,890011,890012))
             + (SELECT COUNT(*) FROM t_notification_template WHERE template_code LIKE 'NOTIFICATION!_ADMIN!_%' ESCAPE '!')
             + (SELECT COUNT(*) FROM t_notification_provider WHERE provider_id IN (890004,890006,890007,890008)
                    OR provider_code IN ('NOTIFICATION_TEST_EMAIL','NOTIFICATION_TEST_FALLBACK','NOTIFICATION_TEST_SES','NOTIFICATION_ADMIN_UNBOUND'))
             + (SELECT COUNT(*) FROM t_notification_route WHERE route_id IN (890005,890015,890016)
                    OR notification_template_id IN (890001,890011,890012))
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

    /** 插入 SES 测试账号；编码与 application-mock.yml 的 notification.ses.provider-code 一致。 */
    @Insert("INSERT INTO t_notification_provider VALUES(890007,'NOTIFICATION_TEST_SES','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void sesProvider();

    /** 模拟管理员把主账号切换为 SES。 */
    @Update("UPDATE t_notification_route SET primary_provider_id=890007,version=version+1 WHERE route_id=890005")
    void useSes();

    /** 插入两个认证即时发送模板；编码与 application-mock.yml 的 notification.transient-template-codes 一致。 */
    @Insert("""
        INSERT INTO t_notification_template VALUES
        (890011,'CLIENT_EMAIL_VERIFICATION','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
        (890012,'CLIENT_PASSWORD_RESET','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void authenticationTemplates();

    /** 插入两个认证模板的 zh-CN 内容。 */
    @Insert("""
        INSERT INTO t_notification_template_content VALUES
        (890013,890011,'zh-CN',#{verificationSubject},#{verificationBody},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
        (890014,890012,'zh-CN',#{resetSubject},#{resetBody},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void authenticationContents(@Param("verificationSubject") String verificationSubject,
        @Param("verificationBody") String verificationBody, @Param("resetSubject") String resetSubject,
        @Param("resetBody") String resetBody);

    /** 两个认证模板都只路由到 SES 测试账号。 */
    @Insert("""
        INSERT INTO t_notification_route VALUES
        (890015,890011,890007,NULL,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
        (890016,890012,890007,NULL,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    void authenticationRoutes();

    /** 模拟管理员把注册验证模板的主账号切换为测试 Mock 账号（需先插入 890004）。 */
    @Update("UPDATE t_notification_route SET primary_provider_id=890004,version=version+1 WHERE route_id=890015")
    void useMockProviderForVerification();

    /** 模拟管理员停用注册验证模板。 */
    @Update("UPDATE t_notification_template SET status=#{status},version=version+1 WHERE template_id=890011")
    void changeVerificationTemplate(@Param("status") String status);

    /** 全部通知持久化事实（不按来源过滤），用于证明即时发送不落库。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_notification_request) + (SELECT COUNT(*) FROM t_notification_delivery)
             + (SELECT COUNT(*) FROM t_notification_attempt)
             + (SELECT COUNT(*) FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY')
        """)
    int persistedNotificationCount();

    /** 把本例全部尝试的开始时间前移，模拟安全窗口已过，不调整宿主时钟。 */
    @Update("UPDATE t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id JOIN t_notification_request r ON r.request_id=d.request_id SET a.started_time=a.started_time - INTERVAL #{seconds} SECOND WHERE r.source_system LIKE 'notification-test%'")
    void shiftAttemptsBack(@Param("seconds") long seconds);

    /** 模拟进程在外发后、回写前崩溃：仅最新尝试与投递回到 PROCESSING。 */
    @Update("""
        UPDATE t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id
        JOIN t_notification_request r ON r.request_id=d.request_id
        SET a.status='PROCESSING',a.response_code=NULL,a.resolution_source=NULL,a.finished_time=NULL,
            d.status='PROCESSING',d.version=d.version+1
        WHERE r.source_system LIKE 'notification-test%'
          AND a.attempt_id=(SELECT MAX(x.attempt_id) FROM (SELECT attempt_id,delivery_id FROM t_notification_attempt) x
                            WHERE x.delivery_id=a.delivery_id)
        """)
    void interruptSend();

    /** 读取当前用例的 Delivery 状态，不输出 Token 或参数。 */
    @Select("""
        SELECT d.delivery_id,d.status,d.attempt_count,d.version,d.next_retry_time FROM t_notification_delivery d
        JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%'
        ORDER BY d.delivery_id
        """)
    List<Map<String,Object>> deliveries();

    /** 读取发送身份与结果。 */
    @Select("""
        SELECT a.attempt_id,a.delivery_id,a.provider_id,a.provider_request_no,a.provider_message_id,a.status,
            a.response_code,a.resolution_source,a.resolution_code,a.resolved_time
        FROM t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id
        JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%'
        ORDER BY a.attempt_id
        """)
    List<Map<String,Object>> attempts();

    /** 等待本例 afterCommit 异步完全落到可扫描或终态。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%') AND execute_status IN ('INIT','PROCESSING')")
    int activeTasks();

    /** 有恢复事实但不得携带 Payload。 */
    @Select("SELECT event_key,payload_json,execute_status,execute_count,last_error_code FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%')")
    List<Map<String,Object>> events();

    /** 测试时使本例等待任务立即到期；执行仍经过正式 Scanner。 */
    @Update("UPDATE t_compensation_event SET next_execute_at=UTC_TIMESTAMP(6) WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%') AND execute_status='ERROR'")
    void due();

    /** 模拟已到业务重试时间，不调整宿主机器时钟。 */
    @Update("UPDATE t_notification_delivery SET next_retry_time=#{now} WHERE request_id IN (SELECT request_id FROM t_notification_request WHERE source_system LIKE 'notification-test%') AND status='RETRY_WAIT'")
    void retryDue(@Param("now") java.time.Instant now);

    /** 模拟管理员更新当前文案。 */
    @Update("UPDATE t_notification_template_content SET body_template=#{body},version=version+1 WHERE content_id=890002")
    void changeContent(@Param("body") String body);

    /** 模拟管理员修改模板的状态或媒介。 */
    @Update("UPDATE t_notification_template SET status=#{status},medium=#{medium},version=version+1 WHERE template_id=890001")
    void changeTemplate(@Param("status") String status, @Param("medium") String medium);

    /** 模拟管理员修改路由状态。 */
    @Update("UPDATE t_notification_route SET status=#{status},version=version+1 WHERE route_id=890005")
    void changeRoute(@Param("status") String status);

    /** 模拟管理员修改主测试账号的状态或媒介。 */
    @Update("UPDATE t_notification_provider SET status=#{status},medium=#{medium},version=version+1 WHERE provider_id=890004")
    void changeProvider(@Param("status") String status, @Param("medium") String medium);

    /** 模拟账号编码改为没有适配器的值。 */
    @Update("UPDATE t_notification_provider SET provider_code=#{code},version=version+1 WHERE provider_id=890004")
    void changeProviderCode(@Param("code") String code);

    /** 停用当前账号仍允许查询历史尝试。 */
    @Update("UPDATE t_notification_provider SET status='DISABLED',version=version+1 WHERE provider_id=890004")
    void disableProvider();

    /** 只删除本例持久化任务，先确保异步执行已经停止。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type='NOTIFICATION_DELIVERY' AND event_key IN (SELECT CAST(d.delivery_id AS CHAR) FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%')")
    void clearEvents();

    /** 按业务来源清理本例尝试。 */
    @Delete("DELETE a FROM t_notification_attempt a JOIN t_notification_delivery d ON d.delivery_id=a.delivery_id JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%'")
    void clearAttempts();

    /** 按业务来源清理本例投递。 */
    @Delete("DELETE d FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id WHERE r.source_system LIKE 'notification-test%'")
    void clearDeliveries();

    /** 不清理其他调用方数据。 */
    @Delete("DELETE FROM t_notification_request WHERE source_system LIKE 'notification-test%'")
    void clearRequests();

    /** 清理本例路由。 */
    @Delete("DELETE FROM t_notification_route WHERE route_id IN (890005,890015,890016) OR notification_template_id IN (890001,890011,890012)")
    void clearRoute();

    /** 清理本例账号。 */
    @Delete("DELETE FROM t_notification_provider WHERE provider_id IN (890004,890006,890007,890008)")
    void clearProvider();

    /** 清理本例语言内容。 */
    @Delete("DELETE FROM t_notification_template_content WHERE content_id IN (890002,890003,890013,890014) OR template_id IN (890001,890011,890012)")
    void clearContents();

    /** 清理本例模板。 */
    @Delete("DELETE FROM t_notification_template WHERE template_id IN (890001,890011,890012)")
    void clearTemplate();

    /** 没有发送适配器的停用账号，供"本环境已配置"为否的展示。 */
    @Insert("INSERT INTO t_notification_provider VALUES(890008,'NOTIFICATION_ADMIN_UNBOUND','EMAIL','DISABLED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    void unboundProvider();

    /** 一条普通通知发送记录：默认参数含 name、amount，两个收件人；邮箱只有合成 Token。 */
    @Insert("""
        INSERT INTO t_notification_request VALUES
        (890021,NULL,'notification-test-admin','refund:R-0001',890001,'zh-CN','{"name":"Alice","amount":"10.00"}',890005,#{created},#{created})
        """)
    void recordRequest(@Param("created") java.time.Instant created);

    /** 第一个收件人已发送；第二个收件人覆盖语言与参数、等待重试。 */
    @Insert("""
        INSERT INTO t_notification_delivery VALUES
        (890022,890021,'vlt_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',NULL,NULL,'SENT',1,NULL,2,#{created},#{created}),
        (890023,890021,'vlt_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','en-US','{"name":"Bob"}','RETRY_WAIT',2,#{created},3,#{created},#{created})
        """)
    void recordDeliveries(@Param("created") java.time.Instant created);

    /** 三次尝试：一次受理，两次被拒（第二次走备用账号）。 */
    @Insert("""
        INSERT INTO t_notification_attempt VALUES
        (890024,890022,890004,'admin-test-req-1','admin-test-msg-1','ACCEPTED','ACCEPTED','SEND_RESPONSE',NULL,#{created},#{created},#{created},#{created},#{created}),
        (890025,890023,890004,'admin-test-req-2',NULL,'FAILED','THROTTLED','SEND_RESPONSE',NULL,#{created},#{created},#{created},#{created},#{created}),
        (890026,890023,890006,'admin-test-req-3',NULL,'FAILED','THROTTLED','SEND_RESPONSE',NULL,#{created},#{created},#{created},#{created},#{created})
        """)
    void recordAttempts(@Param("created") java.time.Instant created);

    /** 当前模板状态与版本。 */
    @Select("SELECT status, version FROM t_notification_template WHERE template_code=#{code}")
    Map<String, Object> templateState(@Param("code") String code);

    /** 当前语言内容。 */
    @Select("""
        SELECT c.subject_template, c.body_template, c.version FROM t_notification_template_content c
        JOIN t_notification_template t ON t.template_id=c.template_id WHERE t.template_code=#{code} AND c.locale=#{locale}
        """)
    Map<String, Object> contentState(@Param("code") String code, @Param("locale") String locale);

    /** 当前路由，账号以编码表示。 */
    @Select("""
        SELECT p.provider_code AS primary_code, f.provider_code AS fallback_code, r.status, r.version
        FROM t_notification_route r JOIN t_notification_template t ON t.template_id=r.notification_template_id
        JOIN t_notification_provider p ON p.provider_id=r.primary_provider_id
        LEFT JOIN t_notification_provider f ON f.provider_id=r.fallback_provider_id WHERE t.template_code=#{code}
        """)
    Map<String, Object> routeState(@Param("code") String code);

    /** 当前账号状态与版本。 */
    @Select("SELECT status, version FROM t_notification_provider WHERE provider_code=#{code}")
    Map<String, Object> providerState(@Param("code") String code);

    /** 清理管理接口新建模板的语言内容。 */
    @Delete("DELETE c FROM t_notification_template_content c JOIN t_notification_template t ON t.template_id=c.template_id WHERE t.template_code LIKE 'NOTIFICATION!_ADMIN!_%' ESCAPE '!'")
    void clearAdminContents();

    /** 清理管理接口新建模板的路由。 */
    @Delete("DELETE r FROM t_notification_route r JOIN t_notification_template t ON t.template_id=r.notification_template_id WHERE t.template_code LIKE 'NOTIFICATION!_ADMIN!_%' ESCAPE '!'")
    void clearAdminRoutes();

    /** 清理管理接口新建的模板。 */
    @Delete("DELETE FROM t_notification_template WHERE template_code LIKE 'NOTIFICATION!_ADMIN!_%' ESCAPE '!'")
    void clearAdminTemplates();
}
