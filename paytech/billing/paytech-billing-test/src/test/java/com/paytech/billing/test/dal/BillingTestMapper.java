package com.paytech.billing.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
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

    /** 统计方案下的版本数。 */
    @Select("SELECT COUNT(*) FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id "
        + "WHERE p.plan_code = #{planCode}")
    int countChannelCostVersions(@Param("planCode") String planCode);
}
