package com.paytech.account.test.clear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.account.test.mocker.local.BalanceAccountMocker;
import com.paytech.account.test.mocker.local.LedgerFixtureJdbc;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * Account 测试数据的基线检查与全局清理（共库过渡期，design/10 §3.1 的 AccountDataCleaner）
 * <li>本步 account 没有自有表：只断言共库 mor_ledger 的 t_ledger_book / t_account 为空且 STANDARD 科目已初始化，并只删除本例夹具插入的行。</li>
 * <li>第 11b 步 mor_account 建立后改为清理自有余额表与 t_compensation_event。</li>
 */
@TestComponent
@Order(1)
public class AccountDataCleaner implements Clear {
    /** ledger 初始化 DML 中 STANDARD 科目表的核心科目数。 */
    static final int STANDARD_CHART_ACCOUNTS = 29;

    private static boolean reusable = true;

    private final LedgerFixtureJdbc fixture;
    private final BalanceAccountMocker mocker;

    public AccountDataCleaner(LedgerFixtureJdbc fixture, BalanceAccountMocker mocker) {
        this.fixture = fixture;
        this.mocker = mocker;
    }

    /** 检查环境可复用、ledger 初始化 DML 已执行且共库账簿与账户为空。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_ledger 后重新运行");
        assertEquals(STANDARD_CHART_ACCOUNTS, count("SELECT COUNT(*) FROM t_chart_account WHERE chart_id = "
            + BalanceAccountMocker.STANDARD_CHART_ID + " AND id < 1000000"), "mor_ledger 未执行 ledger-initial-data.sql");
        assertEquals(0L, count("SELECT COUNT(*) FROM t_account"), "测试开始前 t_account 应为空");
        assertEquals(0L, count("SELECT COUNT(*) FROM t_ledger_book"), "测试开始前 t_ledger_book 应为空");
    }

    /** 先删账户再删账簿，只删本例插入的行；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            mocker.drainAccountIds().forEach(id -> fixture.jdbc().update("DELETE FROM t_account WHERE id = ?", id));
            mocker.drainBookIds().forEach(id -> fixture.jdbc().update("DELETE FROM t_ledger_book WHERE id = ?", id));
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }

    private long count(String sql) {
        return fixture.jdbc().queryForObject(sql, Long.class);
    }
}
