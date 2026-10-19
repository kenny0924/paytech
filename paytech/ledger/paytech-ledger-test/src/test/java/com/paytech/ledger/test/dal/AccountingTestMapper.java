package com.paytech.ledger.test.dal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.JournalLine;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;

/**
 * Ledger 测试专用读写：前置状态构造、只读断言与全局清理；只触碰 mor_ledger 的 31 张表
 * <li>初始化 DML 的固定 ID 行（小于 1,000,000）是只读全局基础数据，清理时保留。</li>
 */
@Mapper
public interface AccountingTestMapper {
    /** 初始化 DML 保留行的 ID 上界。 */
    long RESERVED_ID_LIMIT = 1_000_000L;

    /** 统计 mor_ledger 全部 31 张表中测试可写入的行数（保留表只统计 ID ≥ 1,000,000 的行），按表返回。 */
    @Select("""
        SELECT 't_close_approval' t, COUNT(*) c FROM t_close_approval UNION ALL
        SELECT 't_close_step', COUNT(*) FROM t_close_step UNION ALL
        SELECT 't_period_close_book', COUNT(*) FROM t_period_close_book UNION ALL
        SELECT 't_period_close', COUNT(*) FROM t_period_close UNION ALL
        SELECT 't_ledger_period_balance', COUNT(*) FROM t_ledger_period_balance UNION ALL
        SELECT 't_trial_balance_exception', COUNT(*) FROM t_trial_balance_exception UNION ALL
        SELECT 't_trial_balance_line', COUNT(*) FROM t_trial_balance_line UNION ALL
        SELECT 't_trial_balance_run', COUNT(*) FROM t_trial_balance_run UNION ALL
        SELECT 't_finance_entity', COUNT(*) FROM t_finance_entity UNION ALL
        SELECT 't_ledger_book', COUNT(*) FROM t_ledger_book UNION ALL
        SELECT 't_book_sequence', COUNT(*) FROM t_book_sequence UNION ALL
        SELECT 't_ledger_account', COUNT(*) FROM t_ledger_account UNION ALL
        SELECT 't_fiscal_calendar', COUNT(*) FROM t_fiscal_calendar UNION ALL
        SELECT 't_accounting_period', COUNT(*) FROM t_accounting_period UNION ALL
        SELECT 't_accounting_instruction', COUNT(*) FROM t_accounting_instruction UNION ALL
        SELECT 't_instruction_line', COUNT(*) FROM t_instruction_line UNION ALL
        SELECT 't_journal_entry', COUNT(*) FROM t_journal_entry UNION ALL
        SELECT 't_journal_line', COUNT(*) FROM t_journal_line UNION ALL
        SELECT 't_manual_adjustment', COUNT(*) FROM t_manual_adjustment UNION ALL
        SELECT 't_manual_adjustment_line', COUNT(*) FROM t_manual_adjustment_line UNION ALL
        SELECT 't_adjustment_approval', COUNT(*) FROM t_adjustment_approval UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_close_account_rule', COUNT(*) FROM t_close_account_rule WHERE id >= 1000000 UNION ALL
        SELECT 't_accounting_rule_line', COUNT(*) FROM t_accounting_rule_line WHERE id >= 1000000 UNION ALL
        SELECT 't_accounting_rule_bundle_ver', COUNT(*) FROM t_accounting_rule_bundle_ver WHERE id >= 1000000 UNION ALL
        SELECT 't_accounting_rule_bundle', COUNT(*) FROM t_accounting_rule_bundle WHERE id >= 1000000 UNION ALL
        SELECT 't_currency_spec', COUNT(*) FROM t_currency_spec WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_of_accounts', COUNT(*) FROM t_chart_of_accounts WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_account', COUNT(*) FROM t_chart_account WHERE id >= 1000000
        """)
    List<Map<String, Object>> countWritableRows();

    /** 统计初始化 DML 的保留行数，用于确认 mor_ledger 已执行 ledger-initial-data.sql。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_currency_spec WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_of_accounts WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_account WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_close_account_rule WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_accounting_rule_bundle WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_accounting_rule_bundle_ver WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_accounting_rule_line WHERE id < 1000000)")
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

    /** 插入测试科目表（主键须 >= 1000000，由全局清理删除）。 */
    @Insert("INSERT INTO t_chart_of_accounts (id, chart_code, chart_name, status, create_time, update_time) "
        + "VALUES (#{id}, #{chartCode}, #{chartName}, #{status}, #{createTime}, #{updateTime})")
    void insertChartOfAccounts(ChartOfAccounts chart);

    /** 插入测试科目（主键须 >= 1000000，由全局清理删除）。 */
    @Insert("INSERT INTO t_chart_account (id, chart_id, parent_account_id, account_code, account_name, account_class, "
        + "normal_side, posting_allowed, create_time, update_time) VALUES (#{id}, #{chartId}, #{parentAccountId}, "
        + "#{accountCode}, #{accountName}, #{accountClass}, #{normalSide}, #{postingAllowed}, #{createTime}, #{updateTime})")
    void insertChartAccount(ChartAccount account);

    /** 直接改变账户状态，构造停用账户场景（本期无停用入口）。 */
    @Update("UPDATE t_ledger_account SET account_status = #{status} WHERE id = #{id}")
    void updateAccountStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改变账簿状态，构造暂停账簿场景（本期无停用入口）。 */
    @Update("UPDATE t_ledger_book SET book_status = #{status} WHERE id = #{id}")
    void updateBookStatus(@Param("id") long id, @Param("status") String status);

    /** 直接改变期间状态，构造关闭期间场景（本期无关账入口）。 */
    @Update("UPDATE t_accounting_period SET status = #{status} WHERE id = #{id}")
    void updatePeriodStatus(@Param("id") long id, @Param("status") String status);

    /** 读取账户身份。 */
    @Select("SELECT * FROM t_ledger_account WHERE id = #{id}")
    LedgerAccount selectAccount(@Param("id") long id);

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

    /** 直接篡改一行分录金额，构造“已过账分录借贷不平”的试算失败场景（第 11b 步起 ledger 不再保存账户投影）。 */
    @Update("UPDATE t_journal_line SET amount = #{amount} WHERE journal_entry_id = #{journalEntryId} AND line_no = #{lineNo}")
    void updateJournalLineAmount(@Param("journalEntryId") long journalEntryId, @Param("lineNo") int lineNo,
        @Param("amount") String amount);

    /** 插入一条 OPEN 的 GENERAL_LEDGER_MISMATCH/ACCOUNT 试算异常，模拟第 11b 步前账户投影核对留下的存量异常。 */
    @Insert("INSERT INTO t_trial_balance_exception (id, run_id, exception_type, reference_type, reference_id, "
        + "difference_amount, status, create_time, update_time) VALUES (#{id}, #{runId}, 'GENERAL_LEDGER_MISMATCH', "
        + "'ACCOUNT', #{accountId}, #{difference}, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertLegacyAccountException(@Param("id") long id, @Param("runId") long runId,
        @Param("accountId") long accountId, @Param("difference") String difference);

    /** 读取账簿最近一次试算的主键。 */
    @Select("SELECT id FROM t_trial_balance_run WHERE ledger_book_id = #{ledgerBookId} ORDER BY create_time DESC, id DESC LIMIT 1")
    long selectLatestTrialRunId(@Param("ledgerBookId") long ledgerBookId);

    /** 账户按全部分录聚合的借贷合计与最大分录序号；每行含 debit_total、credit_total、last_journal_sequence。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN l.entry_side = 'DEBIT' THEN l.amount END), 0) debit_total, "
        + "COALESCE(SUM(CASE WHEN l.entry_side = 'CREDIT' THEN l.amount END), 0) credit_total, "
        + "COALESCE(MAX(e.journal_sequence), 0) last_journal_sequence FROM t_journal_line l "
        + "JOIN t_journal_entry e ON e.id = l.journal_entry_id WHERE l.account_id = #{accountId}")
    Map<String, Object> selectAccountJournalTotals(@Param("accountId") long accountId);

    /** 把调整的一行改指另一账户，模拟第 11b 步前已提交、涉及 Owner 账户的存量调整。 */
    @Update("UPDATE t_manual_adjustment_line SET account_id = #{accountId} WHERE adjustment_id = #{adjustmentId} "
        + "AND line_no = #{lineNo}")
    void updateAdjustmentLineAccount(@Param("adjustmentId") long adjustmentId, @Param("lineNo") int lineNo,
        @Param("accountId") long accountId);

    /** 统计已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);

    /** 读取过账流（聚合）的已消费序号，未消费返回空。 */
    @Select("SELECT last_version FROM t_consumed_aggregate_version WHERE aggregate_type = #{aggregateType} "
        + "AND aggregate_id = #{aggregateId}")
    Long selectConsumedVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId);

    /** 写入过账流的已消费序号，构造关账步骤 3 的 ledger 进度。 */
    @Insert("INSERT INTO t_consumed_aggregate_version (aggregate_type, aggregate_id, last_version, update_time) "
        + "VALUES (#{aggregateType}, #{aggregateId}, #{lastVersion}, UTC_TIMESTAMP(6))")
    void insertConsumedVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
        @Param("lastVersion") long lastVersion);

    /** 改写过账流的已消费序号，构造“ledger 已追平”。 */
    @Update("UPDATE t_consumed_aggregate_version SET last_version = #{lastVersion} WHERE aggregate_type = #{aggregateType} "
        + "AND aggregate_id = #{aggregateId}")
    void updateConsumedVersion(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
        @Param("lastVersion") long lastVersion);

    /** 读取来源事件（account 批次主键）生成的全部指令，按账簿主键、指令类型升序。 */
    @Select("SELECT * FROM t_accounting_instruction WHERE source_system = #{sourceSystem} "
        + "AND source_event_key = #{sourceEventKey} ORDER BY ledger_book_id, instruction_type")
    List<AccountingInstruction> selectSourceInstructions(@Param("sourceSystem") String sourceSystem,
        @Param("sourceEventKey") String sourceEventKey);

    /** 统计全部已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event")
    int countAllConsumedEvents();

    /** 读取账簿在期间内的期间余额投影，按科目主键升序。 */
    @Select("SELECT chart_account_id, opening_balance, period_debit, period_credit, closing_balance, as_of_sequence, "
        + "trial_balance_run_id FROM t_ledger_period_balance WHERE period_id = #{periodId} "
        + "AND ledger_book_id = #{ledgerBookId} ORDER BY chart_account_id")
    List<Map<String, Object>> selectPeriodBalances(@Param("periodId") long periodId,
        @Param("ledgerBookId") long ledgerBookId);

    /** 把期间余额投影的水位改为指定值，构造“已有更高水位”场景。 */
    @Update("UPDATE t_ledger_period_balance SET as_of_sequence = #{asOfSequence} WHERE period_id = #{periodId} "
        + "AND ledger_book_id = #{ledgerBookId}")
    void updatePeriodBalanceSequence(@Param("periodId") long periodId, @Param("ledgerBookId") long ledgerBookId,
        @Param("asOfSequence") long asOfSequence);

    /** 统计账簿的试算次数。 */
    @Select("SELECT COUNT(*) FROM t_trial_balance_run WHERE ledger_book_id = #{ledgerBookId}")
    int countTrialRuns(@Param("ledgerBookId") long ledgerBookId);

    /** 在调用方事务内排他锁定账簿序号行，模拟在途过账。 */
    @Select("SELECT next_value FROM t_book_sequence WHERE ledger_book_id = #{ledgerBookId} FOR UPDATE")
    long lockBookSequence(@Param("ledgerBookId") long ledgerBookId);

    /** 读取期间当前状态码。 */
    @Select("SELECT status FROM t_accounting_period WHERE id = #{id}")
    String selectPeriodStatus(@Param("id") long id);

    /** 读取账户按全部分录聚合、按正常方向计算的净余额。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN l.entry_side = a.normal_side THEN l.amount ELSE -l.amount END), 0) "
        + "FROM t_ledger_account a LEFT JOIN t_journal_line l ON l.account_id = a.id WHERE a.id = #{id}")
    BigDecimal selectAccountNetBalance(@Param("id") long id);

    /** 按账簿、科目代码、核算对象读取账户主键，不存在返回空。 */
    @Select("SELECT a.id FROM t_ledger_account a JOIN t_chart_account c ON c.id = a.chart_account_id "
        + "WHERE a.ledger_book_id = #{ledgerBookId} AND c.account_code = #{accountCode} "
        + "AND a.owner_type = #{ownerType} AND a.owner_id = #{ownerId} AND a.balance_bucket = 'NONE'")
    Long selectAccountId(@Param("ledgerBookId") long ledgerBookId, @Param("accountCode") String accountCode,
        @Param("ownerType") String ownerType, @Param("ownerId") String ownerId);

    /** 读取引用指定账户的全部试算异常主键，按创建顺序。 */
    @Select("SELECT id FROM t_trial_balance_exception WHERE reference_type = 'ACCOUNT' AND reference_id = #{accountId} "
        + "ORDER BY create_time, id")
    List<Long> selectAccountExceptionIds(@Param("accountId") long accountId);

    /** 读取试算异常当前状态码。 */
    @Select("SELECT status FROM t_trial_balance_exception WHERE id = #{id}")
    String selectExceptionStatus(@Param("id") long id);

    /** 统计账簿内指定指令类型的指令数。 */
    @Select("SELECT COUNT(*) FROM t_accounting_instruction WHERE ledger_book_id = #{ledgerBookId} "
        + "AND instruction_type = #{instructionType}")
    int countInstructions(@Param("ledgerBookId") long ledgerBookId, @Param("instructionType") String instructionType);

    /** 读取指令对应的分录头。 */
    @Select("SELECT * FROM t_journal_entry WHERE instruction_id = #{instructionId}")
    JournalEntry selectJournalEntryByInstruction(@Param("instructionId") long instructionId);

    /** 插入一条 VALIDATED 但未过账的人工指令，用于制造关账完整性检查失败。 */
    @Insert("INSERT INTO t_accounting_instruction (id, ledger_book_id, instruction_type, origin_type, status, version, "
        + "create_time, update_time) VALUES (#{id}, #{ledgerBookId}, 'MANUAL_ADJUSTMENT', 'MANUAL', 'VALIDATED', 0, "
        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertPendingInstruction(@Param("id") long id, @Param("ledgerBookId") long ledgerBookId);

    /** 统计期间的关账流程数。 */
    @Select("SELECT COUNT(*) FROM t_period_close WHERE period_id = #{periodId}")
    int countPeriodCloses(@Param("periodId") long periodId);

    /** 统计初始化 DML 预置的 STANDARD_TRANSACTION Bundle 与第 1 版是否仍处于初始状态（是返回 1）。 */
    @Select("SELECT COUNT(*) FROM t_accounting_rule_bundle b JOIN t_accounting_rule_bundle_ver v ON v.id = b.current_ver_id "
        + "WHERE b.id = 5001 AND b.status = 'ACTIVE' AND b.current_ver_id = 5101 AND b.version = 0 "
        + "AND v.status = 'ACTIVE' AND v.effective_to IS NULL")
    int countInitialRuleBaseline();

    /** 把 Bundle 5001 恢复为初始化状态：ACTIVE、当前版本 5101、乐观锁版本 0。 */
    @Update("UPDATE t_accounting_rule_bundle SET status = 'ACTIVE', current_ver_id = 5101, version = 0 WHERE id = 5001")
    void restoreInitialRuleBundle();

    /** 把版本 5101 恢复为初始化状态：ACTIVE 且未失效。 */
    @Update("UPDATE t_accounting_rule_bundle_ver SET status = 'ACTIVE', effective_to = NULL WHERE id = 5101")
    void restoreInitialRuleVersion();

    /** 直接改变规则版本状态，构造“无生效 Bundle 版本”场景；清理组件恢复初始化行。 */
    @Update("UPDATE t_accounting_rule_bundle_ver SET status = #{status} WHERE id = #{id}")
    void updateRuleVersionStatus(@Param("id") long id, @Param("status") String status);

    /** 直接清空 Bundle 当前版本指针，构造“无当前版本”场景；清理组件恢复初始化行。 */
    @Update("UPDATE t_accounting_rule_bundle SET current_ver_id = NULL WHERE id = #{id}")
    void clearRuleBundleCurrentVersion(@Param("id") long id);

    /** 读取规则版本当前状态码。 */
    @Select("SELECT status FROM t_accounting_rule_bundle_ver WHERE id = #{id}")
    String selectRuleVersionStatus(@Param("id") long id);

    /** 读取 Bundle 当前版本指针与乐观锁版本。 */
    @Select("SELECT current_ver_id, version FROM t_accounting_rule_bundle WHERE id = #{id}")
    Map<String, Object> selectRuleBundlePointer(@Param("id") long id);

    /** 统计规则版本的规则行数。 */
    @Select("SELECT COUNT(*) FROM t_accounting_rule_line WHERE bundle_ver_id = #{bundleVerId}")
    int countRuleLines(@Param("bundleVerId") long bundleVerId);

    /** 读取计费事实生成的全部指令，按账簿主键、指令类型升序。 */
    @Select("SELECT * FROM t_accounting_instruction WHERE economic_fact_id = #{economicFactId} "
        + "ORDER BY ledger_book_id, instruction_type")
    List<AccountingInstruction> selectEconomicsInstructions(@Param("economicFactId") long economicFactId);

    /** 读取指令全部指令行，按行号升序。 */
    @Select("SELECT * FROM t_instruction_line WHERE instruction_id = #{instructionId} ORDER BY line_no")
    List<InstructionLine> selectInstructionLines(@Param("instructionId") long instructionId);

    /** 统计全部记账指令数，用于整体回滚断言。 */
    @Select("SELECT COUNT(*) FROM t_accounting_instruction")
    int countAllInstructions();

    /** 统计全部分录头数，用于整体回滚断言。 */
    @Select("SELECT COUNT(*) FROM t_journal_entry")
    int countAllJournalEntries();

    /** 统计全部账户数，用于整体回滚后确认自动建户也已撤销。 */
    @Select("SELECT COUNT(*) FROM t_ledger_account")
    int countAllAccounts();

    /** 按账簿、科目代码、核算对象与余额桶读取账户主键，不存在返回空。 */
    @Select("SELECT a.id FROM t_ledger_account a JOIN t_chart_account c ON c.id = a.chart_account_id "
        + "WHERE a.ledger_book_id = #{ledgerBookId} AND c.account_code = #{accountCode} "
        + "AND a.owner_type = #{ownerType} AND a.owner_id = #{ownerId} AND a.balance_bucket = #{bucket}")
    Long selectBucketAccountId(@Param("ledgerBookId") long ledgerBookId, @Param("accountCode") String accountCode,
        @Param("ownerType") String ownerType, @Param("ownerId") String ownerId, @Param("bucket") String bucket);

    /** 读取期间重开次数。 */
    @Select("SELECT reopen_count FROM t_accounting_period WHERE id = #{id}")
    int selectPeriodReopenCount(@Param("id") long id);

    /** 直接改变日历财年起始月，构造“当前期间为财年最后期间”或“非年末期间”场景（日历无修改入口）。 */
    @Update("UPDATE t_fiscal_calendar SET year_start_month = #{yearStartMonth} WHERE id = #{id}")
    void updateCalendarYearStartMonth(@Param("id") long id, @Param("yearStartMonth") int yearStartMonth);
}
