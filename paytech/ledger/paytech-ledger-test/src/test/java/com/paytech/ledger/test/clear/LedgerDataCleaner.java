package com.paytech.ledger.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.ledger.test.dal.AccountingTestMapper;

/**
 * Ledger 本地 mor_ledger 测试数据的基线检查与全局清理（由 FinanceDataCleaner 收缩，design/10 §3.1）
 * <li>BeforeEach 只检查空基线，发现非测试遗留数据时失败而不删除，避免误清本地开发数据。</li>
 * <li>AfterEach 按依赖逆序删除全部测试写入，保留初始化 DML 的固定 ID 行；清理失败后环境标记为不可复用。</li>
 * <li>规则 Bundle 三表按保留表处理：删除测试新建的版本与规则行后，把 Bundle 5001 与版本 5101 恢复为初始化状态。</li>
 * <li>STANDARD 保留科目只允许测试改变启停状态，清理时恢复为 ACTIVE；编号、上级与名称不得在测试中修改。</li>
 */
@TestComponent
@Order(1)
public class LedgerDataCleaner implements Clear {
    /**
     * 初始化 DML 保留行数：STANDARD 1 + 科目 74（一级汇总 19 + 二级汇总 26 + 三级末级 29）+ 结转规则 7
     * + 规则 Bundle 2 + 规则版本 2 + 规则行 10 + 26（第 11b 步增加 STANDARD_ADJUSTMENT Bundle 5002、版本 5102 与规则行
     * 5301–5332；2026-10-19 删除 Owner 角色规则行 5202、5208、5315–5320，Owner 控制科目行改由 account 入账批次流水生成；
     * 同日 STANDARD 重建为分级科目树，2026-10-20 改为三级：一级汇总 3101–3119、二级汇总 3201–3226；同日删除 t_currency_spec 副本的 USD 行，
     * 币种规则改由 Platform 快照提供）。
     */
    static final int RESERVED_ROWS = 122;

    private static final List<String> FULLY_OWNED_TABLES = List.of("t_consumed_event", "t_consumed_aggregate_version",
        "t_compensation_event", "t_close_approval",
        "t_close_step",
        "t_period_close_book", "t_period_close", "t_ledger_period_balance",
        "t_trial_balance_exception", "t_trial_balance_line", "t_trial_balance_run", "t_journal_line", "t_journal_entry",
        "t_instruction_line", "t_accounting_instruction", "t_adjustment_approval", "t_manual_adjustment_line",
        "t_manual_adjustment", "t_chart_account_change", "t_account_no_sequence", "t_ledger_account",
        "t_book_sequence", "t_ledger_book", "t_accounting_period",
        "t_fiscal_calendar", "t_finance_entity");

    private static final List<String> RESERVED_TABLES = List.of("t_close_account_rule", "t_accounting_rule_line",
        "t_accounting_rule_bundle_ver", "t_accounting_rule_bundle", "t_chart_account", "t_chart_of_accounts");

    private static boolean reusable = true;

    private final AccountingTestMapper mapper;

    public LedgerDataCleaner(AccountingTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用、初始化 DML 已执行且没有任何测试可写数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_ledger 后重新运行");
        assertEquals(RESERVED_ROWS, mapper.countReservedRows(),
            "mor_ledger 未执行 ledger-initial-data.sql 或保留行被修改");
        assertEquals(0, mapper.countInactiveReservedChartAccounts(), "STANDARD 保留科目存在非 ACTIVE 状态");
        assertEquals(1, mapper.countInitialRuleBaseline(),
            "STANDARD_TRANSACTION Bundle 5001 / 版本 5101 未处于初始化状态（ACTIVE、当前版本 5101、effective_to 为空）");
        List<Map<String, Object>> counts = mapper.countWritableRows();
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            FULLY_OWNED_TABLES.forEach(mapper::deleteAll);
            RESERVED_TABLES.forEach(mapper::deleteNonReserved);
            mapper.restoreInitialRuleBundle();
            mapper.restoreInitialRuleVersion();
            mapper.restoreReservedChartAccounts();
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
