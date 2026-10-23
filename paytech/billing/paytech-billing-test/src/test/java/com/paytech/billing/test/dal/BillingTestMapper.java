package com.paytech.billing.test.dal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Billing 测试专用读取、前置数据与全局清理；只触碰 mor_billing 的 10 张表
 * <li>初始化 DML 的保留行（DEFAULT 与 AE_BUSINESS_DAYS 日历）是只读全局基础数据，清理时保留；AE_BUSINESS_DAYS 由
 * changes/2026-10-23-business-calendar-ae.sql 补登，本机执行前允许缺失。</li>
 * <li>前置数据只模拟后续业务切片才会产生的事实、引用、规则行、行链接与日历例外日；本期人工登记均经真实接口写入。</li>
 */
@Mapper
public interface BillingTestMapper {

    /** 保留的日历代码（初始化 DML）。 */
    String RESERVED_CALENDAR = "DEFAULT";

    /** 统计 mor_billing 全部 10 张表中测试可写入的行数（保留表只统计非保留行），按表返回。 */
    @Select("""
        SELECT 't_billing_fact' t, COUNT(*) c FROM t_billing_fact UNION ALL
        SELECT 't_billing_fact_ref', COUNT(*) FROM t_billing_fact_ref UNION ALL
        SELECT 't_billing_fact_pricing', COUNT(*) FROM t_billing_fact_pricing UNION ALL
        SELECT 't_billing_fact_line', COUNT(*) FROM t_billing_fact_line UNION ALL
        SELECT 't_billing_fact_line_link', COUNT(*) FROM t_billing_fact_line_link UNION ALL
        SELECT 't_business_calendar_day', COUNT(*) FROM t_business_calendar_day UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event UNION ALL
        SELECT 't_business_calendar', COUNT(*) FROM t_business_calendar
            WHERE calendar_code NOT IN ('DEFAULT', 'AE_BUSINESS_DAYS')
        """)
    List<Map<String, Object>> countWritableRows();

    /** 统计初始化 DML 的保留行数（DEFAULT 日历），用于确认 mor_billing 已执行 billing-initial-data.sql。 */
    @Select("SELECT COUNT(*) FROM t_business_calendar WHERE calendar_code = 'DEFAULT'")
    int countReservedRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 删除非保留日历。 */
    @Delete("DELETE FROM t_business_calendar WHERE calendar_code NOT IN ('DEFAULT', 'AE_BUSINESS_DAYS')")
    void deleteNonReservedCalendars();

    /** 读取计费事实头的来源方式、状态、版本、主体、复核、业务时间、发布字段与更新时间。 */
    @Select("SELECT origin_type, status, fact_version, client_id, maker_id, checker_id, approved_time, business_time, "
        + "published_at, supersedes_fact_id, parent_fact_id, update_time FROM t_billing_fact WHERE id = #{factId}")
    Map<String, Object> selectBillingFact(@Param("factId") long factId);

    /** 计费事实的全部明细行（组件、金额、目标余额桶与来源规则字段），按行号升序。 */
    @Select("SELECT line_no, component_type, posted_amount, basis_amount, raw_amount, rate_value, target_bucket, "
        + "source_rule_type, source_evidence FROM t_billing_fact_line WHERE fact_id = #{factId} ORDER BY line_no")
    List<Map<String, Object>> selectBillingFactLines(@Param("factId") long factId);

    /** 计费事实的全部引用（含渠道外部交易号两项），按引用类型升序。 */
    @Select("SELECT ref_type, ref_id, external_reference_type, external_transaction_id FROM t_billing_fact_ref "
        + "WHERE fact_id = #{factId} ORDER BY ref_type")
    List<Map<String, Object>> selectBillingFactRefs(@Param("factId") long factId);

    /** 某计费事实的 BILLING_FACT_RECORDED 补偿任务（任意版本）及其执行状态。 */
    @Select("SELECT event_key, sub_event_type, execute_status, payload_json FROM t_compensation_event "
        + "WHERE event_type = #{eventType} AND event_key LIKE CONCAT('BILLING_FACT:', #{factId}, ':%')")
    List<Map<String, Object>> selectFactCompensationEvents(@Param("eventType") String eventType,
        @Param("factId") long factId);

    /**
     * 插入一条已有计费事实（前置数据）：只填 NOT NULL 列与账簿主体、状态、版本和发布时间
     * <li>模拟后续自动事实切片登记的 PAYMENT 事实或已批准未发布的事实；引用与明细行按需另插。</li>
     */
    @Insert("INSERT INTO t_billing_fact (id, fact_type, origin_type, source_system, source_event_key, source_version, "
        + "source_payload_hash, client_id, entity_no, local_partner_id, finance_entity_id, currency, gross_amount, "
        + "business_time, status, fact_version, approved_time, published_at, create_time, update_time) VALUES (#{id}, "
        + "#{factType}, #{originType}, 'PAYMENT', #{sourceEventKey}, 1, REPEAT('0', 64), #{clientId}, #{entityNo}, "
        + "#{localPartnerId}, #{financeEntityId}, #{currency}, 10, UTC_TIMESTAMP(6), #{status}, #{factVersion}, "
        + "UTC_TIMESTAMP(6), #{publishedAt}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertBillingFact(@Param("id") long id, @Param("factType") String factType,
        @Param("originType") String originType, @Param("sourceEventKey") String sourceEventKey,
        @Param("clientId") Long clientId, @Param("entityNo") String entityNo, @Param("localPartnerId") Long localPartnerId,
        @Param("financeEntityId") Long financeEntityId, @Param("currency") String currency, @Param("status") String status,
        @Param("factVersion") long factVersion, @Param("publishedAt") Instant publishedAt);

    /** 插入一条计费事实引用（前置数据），不带渠道外部交易号。 */
    default void insertBillingFactRef(long id, long factId, String refType, String refId) {
        insertBillingFactRef(id, factId, refType, refId, null, null);
    }

    /**
     * 插入一条计费事实引用（前置数据）
     * <li>模拟后续自动事实切片登记的 CHANNEL_TRANSACTION_REF：外部交易号两项仅此类型非空。</li>
     */
    @Insert("INSERT INTO t_billing_fact_ref (id, fact_id, ref_type, ref_id, external_reference_type, "
        + "external_transaction_id, create_time) VALUES (#{id}, #{factId}, #{refType}, #{refId}, "
        + "#{externalReferenceType}, #{externalTransactionId}, UTC_TIMESTAMP(6))")
    void insertBillingFactRef(@Param("id") long id, @Param("factId") long factId, @Param("refType") String refType,
        @Param("refId") String refId, @Param("externalReferenceType") String externalReferenceType,
        @Param("externalTransactionId") String externalTransactionId);

    /**
     * 插入一条规则计算明细行（前置数据）：模拟后续自动事实切片写入的费率、基数与 JSON 计算证据
     * <li>evidenceJson 为 JSON 对象文本或空。</li>
     */
    @Insert("INSERT INTO t_billing_fact_line (id, fact_id, line_no, component_type, payer_role, payer_id, payee_role, "
        + "payee_id, basis_amount, rate_value, raw_amount, posted_amount, effect_direction, source_rule_type, "
        + "source_rule_version_id, source_rule_component_id, source_evidence, create_time) VALUES (#{id}, #{factId}, "
        + "#{lineNo}, #{componentType}, #{payerRole}, #{payerId}, #{payeeRole}, #{payeeId}, #{basisAmount}, #{rateValue}, "
        + "#{rawAmount}, #{postedAmount}, #{direction}, #{sourceRuleType}, #{sourceRuleVersionId}, "
        + "#{sourceRuleComponentId}, #{evidenceJson}, UTC_TIMESTAMP(6))")
    void insertRuleLine(@Param("id") long id, @Param("factId") long factId, @Param("lineNo") int lineNo,
        @Param("componentType") String componentType, @Param("payerRole") String payerRole,
        @Param("payerId") String payerId, @Param("payeeRole") String payeeRole, @Param("payeeId") String payeeId,
        @Param("basisAmount") BigDecimal basisAmount, @Param("rateValue") BigDecimal rateValue,
        @Param("rawAmount") BigDecimal rawAmount, @Param("postedAmount") BigDecimal postedAmount,
        @Param("direction") String direction, @Param("sourceRuleType") String sourceRuleType,
        @Param("sourceRuleVersionId") Long sourceRuleVersionId, @Param("sourceRuleComponentId") Long sourceRuleComponentId,
        @Param("evidenceJson") String evidenceJson);

    /** 插入一条明细行链接（前置数据，本期无生产写入）。 */
    @Insert("INSERT INTO t_billing_fact_line_link (id, source_line_id, target_line_id, link_type, linked_amount, "
        + "create_time) VALUES (#{id}, #{sourceLineId}, #{targetLineId}, #{linkType}, #{linkedAmount}, UTC_TIMESTAMP(6))")
    void insertLineLink(@Param("id") long id, @Param("sourceLineId") long sourceLineId,
        @Param("targetLineId") long targetLineId, @Param("linkType") String linkType,
        @Param("linkedAmount") BigDecimal linkedAmount);

    /** 插入一条测试专用工作日日历（前置数据，本服务只读；清理时删除）。 */
    @Insert("INSERT INTO t_business_calendar (calendar_code, display_name, timezone, status, create_time, update_time) "
        + "VALUES (#{calendarCode}, #{displayName}, #{timezone}, #{status}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertCalendar(@Param("calendarCode") String calendarCode, @Param("displayName") String displayName,
        @Param("timezone") String timezone, @Param("status") String status);

    /** 插入一条日历例外日（前置数据，本服务只读）。 */
    @Insert("INSERT INTO t_business_calendar_day (id, calendar_code, calendar_date, day_type, note, create_time) "
        + "VALUES (#{id}, #{calendarCode}, #{calendarDate}, #{dayType}, #{note}, UTC_TIMESTAMP(6))")
    void insertCalendarDay(@Param("id") long id, @Param("calendarCode") String calendarCode,
        @Param("calendarDate") LocalDate calendarDate, @Param("dayType") String dayType, @Param("note") String note);

    /**
     * 写入事实的余额到期时间（前置数据）
     * <li>模拟后续 PAYMENT 切片由 billing 计算并冻结的 balance_available_time；本期无生产写入路径。</li>
     */
    @Update("UPDATE t_billing_fact SET balance_available_time = #{balanceAvailableTime} WHERE id = #{factId}")
    void updateBalanceAvailableTime(@Param("factId") long factId,
        @Param("balanceAvailableTime") Instant balanceAvailableTime);

    /** 写入事实的派生来源（前置数据，模拟后续退款等派生事实切片）。 */
    @Update("UPDATE t_billing_fact SET parent_fact_id = #{parentFactId} WHERE id = #{factId}")
    void updateParentFact(@Param("factId") long factId, @Param("parentFactId") long parentFactId);

    /** 按来源系统与来源事件键读取事实头（自动 PAYMENT 事实断言）。 */
    @Select("SELECT id, fact_type, origin_type, status, fact_version, source_version, source_payload_hash, client_id, "
        + "entity_no, local_partner_id, finance_entity_id, currency, gross_amount, business_time, balance_available_time, "
        + "approved_time, maker_id, checker_id FROM t_billing_fact WHERE source_system = #{sourceSystem} "
        + "AND source_event_key = #{sourceEventKey}")
    List<Map<String, Object>> selectFactsBySource(@Param("sourceSystem") String sourceSystem,
        @Param("sourceEventKey") String sourceEventKey);

    /** 计费事实总数。 */
    @Select("SELECT COUNT(*) FROM t_billing_fact")
    int countFacts();

    /** 计费事实的全部明细行（付方 / 收方、计算证据列与来源规则字段），按行号升序。 */
    @Select("SELECT line_no, component_type, payer_role, payer_id, payee_role, payee_id, basis_amount, rate_value, "
        + "raw_amount, posted_amount, effect_direction, target_bucket, source_rule_type, source_rule_version_id, "
        + "source_price_id, source_rule_component_id, source_evidence FROM t_billing_fact_line WHERE fact_id = #{factId} "
        + "ORDER BY line_no")
    List<Map<String, Object>> selectFactLineDetails(@Param("factId") long factId);

    /** 计费事实的定价输入。 */
    @Select("SELECT * FROM t_billing_fact_pricing WHERE fact_id = #{factId}")
    Map<String, Object> selectFactPricing(@Param("factId") long factId);

    /** 已登记消费的事件数（HANDLER_MANAGED：Handler 成功后才登记）。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);

    /** 补偿任务总数。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event")
    int countCompensationEvents();
}
