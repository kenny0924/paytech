package com.paytech.account.test.mocker.local;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.id.IdGenerator;

/**
 * 余额查询前置事实：直接写入共库的 OE 账簿与带余额投影的账户（任务卡 07 第 4 节，共库过渡）
 * <li>科目使用 ledger 初始化 DML 的 STANDARD 科目表；正常方向取自科目，净余额按入参写入投影列。</li>
 * <li>记录本例插入的主键，由 AccountDataCleaner 定向删除；第 11b 步改为写 account 自有余额表。</li>
 */
@TestComponent
public class BalanceAccountMocker {
    /** ledger 初始化 DML 中 STANDARD 科目表的固定主键。 */
    public static final long STANDARD_CHART_ID = 2001L;

    private final IdGenerator idGenerator;
    private final LedgerFixtureJdbc fixture;
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> accountIds = new ArrayList<>();

    public BalanceAccountMocker(IdGenerator idGenerator, LedgerFixtureJdbc fixture) {
        this.idGenerator = idGenerator;
        this.fixture = fixture;
    }

    /** 插入 OPEN 的 OE 币种账簿，返回主键。 */
    public long mockOperatingEntityBook(String entityNo, String currency) {
        long id = idGenerator.nextId();
        Timestamp now = now();
        fixture.jdbc().update("INSERT INTO t_ledger_book (id, entity_type, entity_id, currency, chart_id, book_status, "
            + "version, create_time, update_time) VALUES (?, 'OPERATING_ENTITY', ?, ?, ?, 'OPEN', 0, ?, ?)", id, entityNo,
            currency, STANDARD_CHART_ID, now, now);
        bookIds.add(id);
        return id;
    }

    /** 在 STANDARD 科目下插入 ACTIVE 账户，余额投影为给定净额（借贷合计按正常方向折算）。 */
    public long mockAccount(long ledgerBookId, String accountCode, OwnerType ownerType, String ownerId,
        BalanceBucket bucket, String netBalance) {
        Long chartAccountId = fixture.jdbc().queryForObject(
            "SELECT id FROM t_chart_account WHERE chart_id = ? AND account_code = ?", Long.class, STANDARD_CHART_ID,
            accountCode);
        String normalSide = fixture.jdbc().queryForObject("SELECT normal_side FROM t_chart_account WHERE id = ?",
            String.class, chartAccountId);
        BigDecimal net = new BigDecimal(netBalance);
        BigDecimal debit = "DEBIT".equals(normalSide) ? net : BigDecimal.ZERO;
        BigDecimal credit = "DEBIT".equals(normalSide) ? BigDecimal.ZERO : net;
        long id = idGenerator.nextId();
        Timestamp now = now();
        fixture.jdbc().update("INSERT INTO t_account (id, ledger_book_id, chart_account_id, owner_type, owner_id, "
            + "balance_bucket, normal_side, account_status, debit_total, credit_total, net_balance, last_journal_sequence, "
            + "balance_version, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0, 0, ?, ?)", id,
            ledgerBookId, chartAccountId, ownerType.getCode(), ownerId, bucket.getCode(), normalSide, debit, credit, net,
            now, now);
        accountIds.add(id);
        return id;
    }

    /** 本例插入的账户主键（清理用），返回后清空记录。 */
    public List<Long> drainAccountIds() {
        List<Long> ids = List.copyOf(accountIds);
        accountIds.clear();
        return ids;
    }

    /** 本例插入的账簿主键（清理用），返回后清空记录。 */
    public List<Long> drainBookIds() {
        List<Long> ids = List.copyOf(bookIds);
        bookIds.clear();
        return ids;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Timestamp now() {
        return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
