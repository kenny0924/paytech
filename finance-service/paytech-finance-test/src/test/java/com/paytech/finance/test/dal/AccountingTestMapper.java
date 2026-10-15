package com.paytech.finance.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.dataobject.CurrencySpec;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;

/**
 * Accounting 测试专用读写：前置状态构造、只读断言与全局清理
 * <li>初始化 DML 的固定 ID 行（小于 1,000,000）是只读全局基础数据，清理时保留。</li>
 */
@Mapper
public interface AccountingTestMapper {
    /** 初始化 DML 保留行的 ID 上界。 */
    long RESERVED_ID_LIMIT = 1_000_000L;

    /** 统计 16 张 Accounting 表中测试可写入的行数，按表返回。 */
    @Select("""
        SELECT 't_finance_entity' t, COUNT(*) c FROM t_finance_entity UNION ALL
        SELECT 't_ledger_book', COUNT(*) FROM t_ledger_book UNION ALL
        SELECT 't_book_sequence', COUNT(*) FROM t_book_sequence UNION ALL
        SELECT 't_account', COUNT(*) FROM t_account UNION ALL
        SELECT 't_fiscal_calendar', COUNT(*) FROM t_fiscal_calendar UNION ALL
        SELECT 't_accounting_period', COUNT(*) FROM t_accounting_period UNION ALL
        SELECT 't_accounting_instruction', COUNT(*) FROM t_accounting_instruction UNION ALL
        SELECT 't_instruction_line', COUNT(*) FROM t_instruction_line UNION ALL
        SELECT 't_journal_entry', COUNT(*) FROM t_journal_entry UNION ALL
        SELECT 't_journal_line', COUNT(*) FROM t_journal_line UNION ALL
        SELECT 't_manual_adjustment', COUNT(*) FROM t_manual_adjustment UNION ALL
        SELECT 't_manual_adjustment_line', COUNT(*) FROM t_manual_adjustment_line UNION ALL
        SELECT 't_adjustment_approval', COUNT(*) FROM t_adjustment_approval UNION ALL
        SELECT 't_currency_spec', COUNT(*) FROM t_currency_spec WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_of_accounts', COUNT(*) FROM t_chart_of_accounts WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_account', COUNT(*) FROM t_chart_account WHERE id >= 1000000
        """)
    List<Map<String, Object>> countWritableRows();

    /** 统计初始化 DML 的保留行数，用于确认本地库已执行 finance-initial-data.sql。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_currency_spec WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_of_accounts WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_account WHERE id < 1000000)")
    int countReservedRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 删除初始化表中非保留 ID 的测试行。 */
    @Delete("DELETE FROM ${table} WHERE id >= 1000000")
    void deleteNonReserved(@Param("table") String table);

    /** 插入测试币种规则。 */
    @Insert("INSERT INTO t_currency_spec (id, currency, minor_unit, rounding_mode, status, create_time, update_time) "
        + "VALUES (#{id}, #{currency}, #{minorUnit}, #{roundingMode}, #{status}, #{createTime}, #{updateTime})")
    void insertCurrencySpec(CurrencySpec spec);

    /** 直接改变账户状态，构造停用账户场景（本期无停用入口）。 */
    @Update("UPDATE t_account SET account_status = #{status} WHERE id = #{id}")
    void updateAccountStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改变账簿状态，构造暂停账簿场景（本期无停用入口）。 */
    @Update("UPDATE t_ledger_book SET book_status = #{status} WHERE id = #{id}")
    void updateBookStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改变期间状态，构造关闭期间场景（本期无关账入口）。 */
    @Update("UPDATE t_accounting_period SET status = #{status} WHERE id = #{id}")
    void updatePeriodStatus(@Param("id") long id, @Param("status") String status);

    /** 读取账户当前投影。 */
    @Select("SELECT * FROM t_account WHERE id = #{id}")
    Account selectAccount(@Param("id") long id);

    /** 读取账簿全部分录头，按序号升序。 */
    @Select("SELECT * FROM t_journal_entry WHERE ledger_book_id = #{ledgerBookId} ORDER BY journal_sequence")
    List<JournalEntry> selectJournalEntries(@Param("ledgerBookId") long ledgerBookId);

    /** 读取一条分录的全部分录行，按行号升序。 */
    @Select("SELECT * FROM t_journal_line WHERE journal_entry_id = #{journalEntryId} ORDER BY line_no")
    List<JournalLine> selectJournalLines(@Param("journalEntryId") long journalEntryId);

    /** 读取账户全部分录行，用于从 Journal 重算余额。 */
    @Select("SELECT * FROM t_journal_line WHERE account_id = #{accountId}")
    List<JournalLine> selectAccountJournalLines(@Param("accountId") long accountId);

    /** 读取调整生成的全部指令。 */
    @Select("SELECT * FROM t_accounting_instruction WHERE manual_adjustment_id = #{manualAdjustmentId}")
    List<AccountingInstruction> selectInstructions(@Param("manualAdjustmentId") long manualAdjustmentId);

    /** 读取调整当前状态。 */
    @Select("SELECT * FROM t_manual_adjustment WHERE id = #{id}")
    ManualAdjustment selectAdjustment(@Param("id") long id);

    /** 读取账簿序号行的下一可分配值。 */
    @Select("SELECT next_value FROM t_book_sequence WHERE ledger_book_id = #{ledgerBookId}")
    Long selectNextSequence(@Param("ledgerBookId") long ledgerBookId);

    /** 统计调整的审批记录数。 */
    @Select("SELECT COUNT(*) FROM t_adjustment_approval WHERE adjustment_id = #{adjustmentId}")
    int countApprovals(@Param("adjustmentId") long adjustmentId);
}
