package com.paytech.account.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.app.ext.job.BalanceReconciliationJobHandler;
import com.paytech.account.core.enums.DiscrepancyStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.mocker.external.LedgerBookMocker;
import com.paytech.billing.api.enums.AmountSource;

/**
 * 日终核对（design/04 §7）：经 XXL-Job Handler 入口逐账簿比对本服务余额与 ledger-api account-totals（DTO Mock），差异写 t_balance_discrepancy
 * <li>人为制造的差异（ledger 渠道预提多 1、ledger 有而 account 无的 Partner 应付）被发现并记录；同日重复核对更新同一记录。</li>
 * <li>两侧一致时不记录；ledger 中找不到余额所属账簿时跳过并记 WARN；阶段 0 不接工单。</li>
 *
 * @see BalanceReconciliationJobHandler
 */
class BalanceReconciliationJobTest extends AbstractBalanceTest {
    private static final String CHANNEL = String.valueOf(CHANNEL_ACCOUNT_ID);

    @Autowired
    private BalanceReconciliationJobHandler job;

    @Test
    void test_discrepanciesRecorded() {
        consumed(payment("100").component(AmountSource.CLIENT_PRINCIPAL, "98").component(AmountSource.CHANNEL_COST, "2")
            .build());
        ledger.mockAccountTotals(OE_BOOK_ID, List.of(
            LedgerBookMocker.total(1, StandardAccountCodes.CHANNEL_FEE_ACCRUAL, "CHANNEL_ACCOUNT", CHANNEL, "NONE",
                "CREDIT", "0.00", "3.00", "3.00"),
            LedgerBookMocker.total(2, StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, "CHANNEL_ACCOUNT", CHANNEL,
                "NONE", "DEBIT", "100.00", "0.00", "100.00"),
            LedgerBookMocker.total(3, StandardAccountCodes.CLIENT_PAYABLE, "CLIENT", String.valueOf(CLIENT_ID), "PENDING",
                "CREDIT", "0.00", "98.00", "98.00"),
            LedgerBookMocker.total(4, StandardAccountCodes.PARTNER_PAYABLE, "LOCAL_PARTNER", String.valueOf(PARTNER_ID),
                "PENDING", "CREDIT", "0.00", "5.00", "5.00")));

        assertEquals(2, job.runOnce());
        assertEquals(2, job.runOnce(), "同日重复核对");

        List<Map<String, Object>> rows = testMapper.selectDiscrepancies();
        assertEquals(2, rows.size(), "同日同账户只保留一条记录");
        Map<String, Object> accrual = rows.get(0);
        Map<String, Object> partner = rows.get(1);
        assertAll(() -> assertEquals(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, accrual.get("account_code")),
            () -> assertEquals(OE_BOOK_ID, ((Number)accrual.get("ledger_book_id")).longValue()),
            () -> assertEquals(0, new BigDecimal("2").compareTo((BigDecimal)accrual.get("account_net_balance"))),
            () -> assertEquals(0, new BigDecimal("3").compareTo((BigDecimal)accrual.get("ledger_net_balance"))),
            () -> assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal)accrual.get("difference_amount"))),
            () -> assertEquals(DiscrepancyStatus.OPEN.getCode(), accrual.get("status")),
            () -> assertEquals(StandardAccountCodes.PARTNER_PAYABLE, partner.get("account_code")),
            () -> assertNull(partner.get("account_net_balance"), "account 没有该余额"),
            () -> assertEquals(0, new BigDecimal("5").compareTo((BigDecimal)partner.get("difference_amount"))));
    }

    @Test
    void test_matchingBalancesRecordNothing() {
        consumed(payment("100").component(AmountSource.CLIENT_PRINCIPAL, "100").build());
        ledger.mockAccountTotals(OE_BOOK_ID, List.of(
            LedgerBookMocker.total(2, StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, "CHANNEL_ACCOUNT", CHANNEL,
                "NONE", "DEBIT", "100.00", "0.00", "100.00"),
            LedgerBookMocker.total(3, StandardAccountCodes.CLIENT_PAYABLE, "CLIENT", String.valueOf(CLIENT_ID), "PENDING",
                "CREDIT", "0.00", "100.00", "100.00")));

        assertEquals(0, job.runOnce());
        assertTrue(testMapper.selectDiscrepancies().isEmpty());
    }

    @Test
    void test_missingLedgerBookSkipped() {
        ledger.clear();
        consumed(payment("10").component(AmountSource.CLIENT_PRINCIPAL, "10").build());

        assertEquals(0, job.runOnce());
        assertTrue(testMapper.selectDiscrepancies().isEmpty());
        assertEquals(List.of("list:OPERATING_ENTITY/" + ENTITY_NO + "/" + CURRENCY), ledger.calls());
    }
}
