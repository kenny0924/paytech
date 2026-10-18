package com.paytech.billing.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Billing 测试专用读取与全局清理；只触碰 mor_billing 的 9 张表（design/10 §3.1）
 * <li>由原 Finance AccountingTestMapper 的渠道成本与经济事实部分摘出；初始化 DML 的固定 ID 行（小于 1,000,000）是只读全局基础数据，清理时保留。</li>
 */
@Mapper
public interface BillingTestMapper {

    /** 统计 mor_billing 全部 9 张表中测试可写入的行数（保留表只统计 ID ≥ 1,000,000 的行），按表返回。 */
    @Select("""
        SELECT 't_channel_cost_plan' t, COUNT(*) c FROM t_channel_cost_plan UNION ALL
        SELECT 't_channel_cost_plan_ver', COUNT(*) FROM t_channel_cost_plan_ver UNION ALL
        SELECT 't_channel_cost_scope', COUNT(*) FROM t_channel_cost_scope UNION ALL
        SELECT 't_channel_cost_component', COUNT(*) FROM t_channel_cost_component UNION ALL
        SELECT 't_economic_fact', COUNT(*) FROM t_economic_fact UNION ALL
        SELECT 't_economic_fact_line', COUNT(*) FROM t_economic_fact_line UNION ALL
        SELECT 't_economic_fact_line_link', COUNT(*) FROM t_economic_fact_line_link UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event UNION ALL
        SELECT 't_currency_spec', COUNT(*) FROM t_currency_spec WHERE id >= 1000000
        """)
    List<Map<String, Object>> countWritableRows();

    /** 统计初始化 DML 的保留行数，用于确认 mor_billing 已执行 billing-initial-data.sql。 */
    @Select("SELECT COUNT(*) FROM t_currency_spec WHERE id < 1000000")
    int countReservedRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 删除初始化表中非保留 ID 的测试行。 */
    @Delete("DELETE FROM ${table} WHERE id >= 1000000")
    void deleteNonReserved(@Param("table") String table);

    /** 读取渠道成本方案状态、乐观锁版本与当前版本号（经 current_ver_id 关联，无当前版本为空）。 */
    @Select("SELECT p.status, p.version, v.cost_ver AS current_cost_ver FROM t_channel_cost_plan p "
        + "LEFT JOIN t_channel_cost_plan_ver v ON v.id = p.current_ver_id WHERE p.plan_code = #{planCode}")
    Map<String, Object> selectChannelCostPlan(@Param("planCode") String planCode);

    /** 读取经济事实头的状态、版本、复核与发布字段。 */
    @Select("SELECT status, fact_version, maker_id, checker_id, approved_time, published_at, supersedes_fact_id "
        + "FROM t_economic_fact WHERE id = #{factId}")
    Map<String, Object> selectEconomicFact(@Param("factId") long factId);

    /** 统计经济事实的明细行数。 */
    @Select("SELECT COUNT(*) FROM t_economic_fact_line WHERE transaction_economics_id = #{factId}")
    int countEconomicFactLines(@Param("factId") long factId);

    /** 某经济事实的 ECONOMIC_FACT_RECORDED 补偿任务（任意版本）及其执行状态。 */
    @Select("SELECT event_key, sub_event_type, execute_status, payload_json FROM t_compensation_event "
        + "WHERE event_type = #{eventType} AND event_key LIKE CONCAT('ECONOMIC_FACT:', #{factId}, ':%')")
    List<Map<String, Object>> selectFactCompensationEvents(@Param("eventType") String eventType,
        @Param("factId") long factId);

    /**
     * 插入一条已有经济事实（前置数据）：交易锚点为空，只填 NOT NULL 列与账簿主体、状态、版本和发布时间
     * <li>模拟后续业务切片登记的 PAYMENT 事实或已批准未发布的事实；明细行不需要时不插入。</li>
     */
    @Insert("INSERT INTO t_economic_fact (id, fact_type, source_system, source_event_key, source_version, "
        + "source_payload_hash, client_id, entity_no, finance_entity_id, currency, gross_amount, status, fact_version, "
        + "approved_time, published_at, create_time, update_time) VALUES (#{id}, #{factType}, 'PAYMENT', #{sourceEventKey}, 1, "
        + "REPEAT('0', 64), #{clientId}, #{entityNo}, #{financeEntityId}, #{currency}, 10, #{status}, #{factVersion}, "
        + "UTC_TIMESTAMP(6), #{publishedAt}, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertEconomicFact(@Param("id") long id, @Param("factType") String factType,
        @Param("sourceEventKey") String sourceEventKey, @Param("clientId") long clientId,
        @Param("entityNo") String entityNo, @Param("financeEntityId") Long financeEntityId,
        @Param("currency") String currency, @Param("status") String status, @Param("factVersion") long factVersion,
        @Param("publishedAt") Instant publishedAt);

    /** 统计方案下的版本数。 */
    @Select("SELECT COUNT(*) FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id "
        + "WHERE p.plan_code = #{planCode}")
    int countChannelCostVersions(@Param("planCode") String planCode);
}
