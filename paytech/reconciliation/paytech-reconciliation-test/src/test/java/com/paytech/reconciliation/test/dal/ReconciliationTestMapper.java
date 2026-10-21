package com.paytech.reconciliation.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reconciliation 测试专用读取与全局清理：只触碰 mor_reconciliation 的 15 张表
 * <li>数据准备走生产 Mapper（ReconciliationDataMocker）；这里只放基线检查、清理与异步完成观察。</li>
 */
@Mapper
public interface ReconciliationTestMapper {

    /** 当前库内的全部表名，按名称排序（Schema 就绪检查）。 */
    @Select("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name")
    List<String> listTables();

    /** 统计 15 张表的行数，按表返回。 */
    @Select("""
        SELECT 't_clearing_account' t, COUNT(*) c FROM t_clearing_account UNION ALL
        SELECT 't_clearing_account_mid', COUNT(*) FROM t_clearing_account_mid UNION ALL
        SELECT 't_platform_txn', COUNT(*) FROM t_platform_txn UNION ALL
        SELECT 't_channel_statement', COUNT(*) FROM t_channel_statement UNION ALL
        SELECT 't_channel_statement_line', COUNT(*) FROM t_channel_statement_line UNION ALL
        SELECT 't_cash_account', COUNT(*) FROM t_cash_account UNION ALL
        SELECT 't_bank_statement', COUNT(*) FROM t_bank_statement UNION ALL
        SELECT 't_bank_statement_line', COUNT(*) FROM t_bank_statement_line UNION ALL
        SELECT 't_expected_bank_movement', COUNT(*) FROM t_expected_bank_movement UNION ALL
        SELECT 't_bank_match', COUNT(*) FROM t_bank_match UNION ALL
        SELECT 't_recon_exception', COUNT(*) FROM t_recon_exception UNION ALL
        SELECT 't_recon_resolution', COUNT(*) FROM t_recon_resolution UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event
        """)
    List<Map<String, Object>> countWritableRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 本地补偿任务数（异步工作是否结束）。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event")
    long countCompensationEvents();

    /** 剩余补偿任务的身份与执行状态，用于异步未结束时的诊断。 */
    @Select("SELECT event_key, event_type, sub_event_type, execute_status, execute_count, last_error_code "
        + "FROM t_compensation_event ORDER BY id")
    List<Map<String, Object>> selectCompensationEvents();

    /** 某个领域事件是否已被消费端登记（消费事务已提交）。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    long countConsumedEvent(@Param("eventId") String eventId);
}
