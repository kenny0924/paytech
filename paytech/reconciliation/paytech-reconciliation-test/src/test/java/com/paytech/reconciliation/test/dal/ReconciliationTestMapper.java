package com.paytech.reconciliation.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Reconciliation 测试专用读取与全局清理；只触碰 mor_reconciliation 的 18 张表（design/10 §3.1），本期无测试写入。 */
@Mapper
public interface ReconciliationTestMapper {

    /** 统计 mor_reconciliation 全部 18 张表的行数，按表返回。 */
    @Select("""
        SELECT 't_channel_cost_confirm' t, COUNT(*) c FROM t_channel_cost_confirm UNION ALL
        SELECT 't_channel_cost_alloc', COUNT(*) FROM t_channel_cost_alloc UNION ALL
        SELECT 't_channel_account', COUNT(*) FROM t_channel_account UNION ALL
        SELECT 't_channel_open_item', COUNT(*) FROM t_channel_open_item UNION ALL
        SELECT 't_channel_debit_item', COUNT(*) FROM t_channel_debit_item UNION ALL
        SELECT 't_duplicate_capture_item', COUNT(*) FROM t_duplicate_capture_item UNION ALL
        SELECT 't_channel_settle_batch', COUNT(*) FROM t_channel_settle_batch UNION ALL
        SELECT 't_channel_settle_item', COUNT(*) FROM t_channel_settle_item UNION ALL
        SELECT 't_channel_transaction_match', COUNT(*) FROM t_channel_transaction_match UNION ALL
        SELECT 't_cash_account', COUNT(*) FROM t_cash_account UNION ALL
        SELECT 't_cash_account_binding', COUNT(*) FROM t_cash_account_binding UNION ALL
        SELECT 't_cash_account_binding_ver', COUNT(*) FROM t_cash_account_binding_ver UNION ALL
        SELECT 't_bank_statement', COUNT(*) FROM t_bank_statement UNION ALL
        SELECT 't_bank_statement_item', COUNT(*) FROM t_bank_statement_item UNION ALL
        SELECT 't_bank_recon_match', COUNT(*) FROM t_bank_recon_match UNION ALL
        SELECT 't_recon_exception', COUNT(*) FROM t_recon_exception UNION ALL
        SELECT 't_recon_resolution', COUNT(*) FROM t_recon_resolution UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event
        """)
    List<Map<String, Object>> countWritableRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);
}
