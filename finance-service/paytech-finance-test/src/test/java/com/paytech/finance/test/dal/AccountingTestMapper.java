package com.paytech.finance.test.dal;

import java.math.BigDecimal;
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
import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;
import com.paytech.finance.test.dal.row.ChannelAccountRow;
import com.paytech.finance.test.dal.row.EconomicComponentRow;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;

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
        SELECT 't_transaction_economic_component', COUNT(*) FROM t_transaction_economic_component UNION ALL
        SELECT 't_transaction_economics', COUNT(*) FROM t_transaction_economics UNION ALL
        SELECT 't_channel_account', COUNT(*) FROM t_channel_account UNION ALL
        SELECT 't_accounting_rule_line', COUNT(*) FROM t_accounting_rule_line WHERE id >= 1000000 UNION ALL
        SELECT 't_accounting_rule_bundle_ver', COUNT(*) FROM t_accounting_rule_bundle_ver WHERE id >= 1000000 UNION ALL
        SELECT 't_accounting_rule_bundle', COUNT(*) FROM t_accounting_rule_bundle WHERE id >= 1000000 UNION ALL
        SELECT 't_currency_spec', COUNT(*) FROM t_currency_spec WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_of_accounts', COUNT(*) FROM t_chart_of_accounts WHERE id >= 1000000 UNION ALL
        SELECT 't_chart_account', COUNT(*) FROM t_chart_account WHERE id >= 1000000
        """)
    List<Map<String, Object>> countWritableRows();

    /** 统计初始化 DML 的保留行数，用于确认本地库已执行 finance-initial-data.sql。 */
    @Select("SELECT (SELECT COUNT(*) FROM t_currency_spec WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_of_accounts WHERE id < 1000000) "
        + "+ (SELECT COUNT(*) FROM t_chart_account WHERE id < 1000000) "
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

    /** 直接篡改账户净余额投影，构造总账投影漂移场景。 */
    @Update("UPDATE t_account SET net_balance = #{netBalance} WHERE id = #{id}")
    void updateAccountNetBalance(@Param("id") long id, @Param("netBalance") String netBalance);

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

    /** 读取账户当前净余额。 */
    @Select("SELECT net_balance FROM t_account WHERE id = #{id}")
    BigDecimal selectAccountNetBalance(@Param("id") long id);

    /** 按账簿、科目代码、核算对象读取账户主键，不存在返回空。 */
    @Select("SELECT a.id FROM t_account a JOIN t_chart_account c ON c.id = a.chart_account_id "
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

    /** 插入交易经济结果夹具。 */
    @Insert("INSERT INTO t_transaction_economics (id, economics_type, source_system, source_event_key, source_version, "
        + "source_payload_hash, order_id, order_context_id, context_format, commercial_context_time, tax_snapshot_id, "
        + "payment_intent_id, payment_attempt_id, payment_succeeded_time, client_id, entity_no, local_partner_id, "
        + "consumer_country_code, payment_method, industry_code, mcc_code, client_agreement_id, partner_agreement_oe_id, "
        + "finance_entity_id, currency, gross_amount, create_time) VALUES (#{id}, #{economicsType}, #{sourceSystem}, "
        + "#{sourceEventKey}, #{sourceVersion}, #{sourcePayloadHash}, #{orderId}, #{orderContextId}, #{contextFormat}, "
        + "#{commercialContextTime}, #{taxSnapshotId}, #{paymentIntentId}, #{paymentAttemptId}, #{paymentSucceededTime}, "
        + "#{clientId}, #{entityNo}, #{localPartnerId}, #{consumerCountryCode}, #{paymentMethod}, #{industryCode}, "
        + "#{mccCode}, #{clientAgreementId}, #{partnerAgreementOeId}, #{financeEntityId}, #{currency}, #{grossAmount}, "
        + "#{createTime})")
    void insertTransactionEconomics(TransactionEconomicsRow row);

    /** 插入交易经济组件夹具。 */
    @Insert("INSERT INTO t_transaction_economic_component (id, transaction_economics_id, component_no, component_type, "
        + "party_type, party_id, basis_amount, raw_amount, posted_amount, effect_direction, source_rule_type, "
        + "source_rule_version_id, source_component_code, source_trigger_code, source_calculation_base, "
        + "calculation_method, rounding_mode, create_time) VALUES (#{id}, #{transactionEconomicsId}, #{componentNo}, "
        + "#{componentType}, #{partyType}, #{partyId}, #{basisAmount}, #{rawAmount}, #{postedAmount}, "
        + "#{effectDirection}, #{sourceRuleType}, #{sourceRuleVersionId}, #{sourceComponentCode}, "
        + "#{sourceTriggerCode}, #{sourceCalculationBase}, #{calculationMethod}, #{roundingMode}, #{createTime})")
    void insertEconomicComponent(EconomicComponentRow row);

    /** 插入 Finance 渠道清算账户夹具。 */
    @Insert("INSERT INTO t_channel_account (id, channel_id, mid_id, entity_no, currency, status, create_time, "
        + "update_time) VALUES (#{id}, #{channelId}, #{midId}, #{entityNo}, #{currency}, #{status}, #{createTime}, "
        + "#{updateTime})")
    void insertChannelAccount(ChannelAccountRow row);

    /** 读取交易经济结果生成的全部指令，按账簿主键、指令类型升序。 */
    @Select("SELECT * FROM t_accounting_instruction WHERE transaction_economics_id = #{transactionEconomicsId} "
        + "ORDER BY ledger_book_id, instruction_type")
    List<AccountingInstruction> selectEconomicsInstructions(@Param("transactionEconomicsId") long transactionEconomicsId);

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
    @Select("SELECT COUNT(*) FROM t_account")
    int countAllAccounts();

    /** 按账簿、科目代码、核算对象与余额桶读取账户主键，不存在返回空。 */
    @Select("SELECT a.id FROM t_account a JOIN t_chart_account c ON c.id = a.chart_account_id "
        + "WHERE a.ledger_book_id = #{ledgerBookId} AND c.account_code = #{accountCode} "
        + "AND a.owner_type = #{ownerType} AND a.owner_id = #{ownerId} AND a.balance_bucket = #{bucket}")
    Long selectBucketAccountId(@Param("ledgerBookId") long ledgerBookId, @Param("accountCode") String accountCode,
        @Param("ownerType") String ownerType, @Param("ownerId") String ownerId, @Param("bucket") String bucket);
}
