package com.paytech.ledger.auto.ext.http.trial;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.TrialBalanceApi;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractTrialBalanceApiTest;

import tools.jackson.databind.JsonNode;

/**
 * 科目余额（实时）：末级与各级汇总行按编号排序、父科目等于下级之和、一级科目借贷平衡、无发生额科目不出现，以及账簿与期间校验。
 *
 * @see TrialBalanceApi#listAccountBalances(String, String)
 */
class AccountBalancesQueryTest extends AbstractTrialBalanceApiTest {
    /** 渠道待清算应付（22310101）。 */
    private static final String UNSETTLED_PAYABLE_CODE = "CHANNEL_UNSETTLED_PAYABLE";

    /** 渠道结算应付（22310102）。 */
    private static final String SETTLEMENT_PAYABLE_CODE = "CHANNEL_SETTLEMENT_PAYABLE";

    /** 渠道清算账户。 */
    private static final String CHANNEL_ACCOUNT_ID = "77";

    private long unsettledPayableId;

    private long settlementPayableId;

    /** 同一二级汇总科目 223101（一级 2231）下的两个渠道应付账户；另有不发生额的保证金账户。 */
    @BeforeEach
    void setUpClassMocks() {
        unsettledPayableId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), UNSETTLED_PAYABLE_CODE,
            LedgerOwnerType.CHANNEL_ACCOUNT, CHANNEL_ACCOUNT_ID, BalanceBucket.NONE);
        settlementPayableId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), SETTLEMENT_PAYABLE_CODE,
            LedgerOwnerType.CHANNEL_ACCOUNT, CHANNEL_ACCOUNT_ID, BalanceBucket.NONE);
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_SECURITY_DEPOSIT,
            LedgerOwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);
    }

    @Test
    void test_leafAndSummaryLinesRollUp() {
        postedIn(mockHistoryPeriod(), bankCashAccountId, capitalAccountId, "100");
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, unsettledPayableId, "50");
        postedIn(ledger.getOpenPeriodId(), unsettledPayableId, settlementPayableId, "30");
        postedIn(ledger.getOpenPeriodId(), liabilityAccountId, bankCashAccountId, "10");

        JsonNode body = get(path(ledger.getLedgerBookId(), ledger.getOpenPeriodId())).assertSuccess(200).json();
        List<JsonNode> lines = new ArrayList<>();
        body.path("lines").forEach(lines::add);

        assertAll(() -> assertEquals(String.valueOf(ledger.getLedgerBookId()), body.path("ledgerBookId").asString()),
            () -> assertEquals(String.valueOf(ledger.getOpenPeriodId()), body.path("periodId").asString()),
            () -> assertEquals("USD", body.path("currency").asString()));
        assertEquals(List.of("1001", "100101", "10010101", "2231", "223101", "22310101", "22310102", "2901", "290101",
            "29010101", "3001", "300101", "30010101"),
            lines.stream().map(line -> line.path("accountNo").asString()).toList(), "无发生额的保证金科目及其上级不出现");
        assertLine(lines.get(0), 1, false, NormalSide.DEBIT, "100.00", "50.00", "10.00", "140.00");
        assertLine(lines.get(1), 2, false, NormalSide.DEBIT, "100.00", "50.00", "10.00", "140.00");
        assertLine(lines.get(2), 3, true, NormalSide.DEBIT, "100.00", "50.00", "10.00", "140.00");
        assertLine(lines.get(3), 1, false, NormalSide.CREDIT, "0.00", "30.00", "80.00", "50.00");
        assertLine(lines.get(4), 2, false, NormalSide.CREDIT, "0.00", "30.00", "80.00", "50.00");
        assertLine(lines.get(5), 3, true, NormalSide.CREDIT, "0.00", "30.00", "50.00", "20.00");
        assertLine(lines.get(6), 3, true, NormalSide.CREDIT, "0.00", "0.00", "30.00", "30.00");
        assertLine(lines.get(7), 1, false, NormalSide.CREDIT, "0.00", "10.00", "0.00", "-10.00");
        assertLine(lines.get(10), 1, false, NormalSide.CREDIT, "100.00", "0.00", "0.00", "100.00");
        assertParent(lines.get(3), lines.get(4));
        assertParent(lines.get(4), lines.get(5));
        assertLevelOneBalanced(lines);
    }

    @Test
    void test_bookOrPeriodRejected() {
        long otherCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE002");
        long otherPeriod = ledgerMocker.mockPeriod(otherCalendar, 2020, 1, HISTORY_END.withDayOfMonth(1), HISTORY_END,
            PeriodStatus.OPEN);

        get(path(ledger.getLedgerBookId(), otherPeriod)).assertBusinessRejected("会计期间不属于账簿主体");
        get(path(123456789L, ledger.getOpenPeriodId())).assertBusinessRejected("账簿不存在");
        get(path(ledger.getLedgerBookId(), 123456789L)).assertBusinessRejected("会计期间不存在");
    }

    @Test
    void test_emptyPeriodReturnsNoLines() {
        assertEquals(0, get(path(ledger.getLedgerBookId(), ledger.getOpenPeriodId())).assertSuccess(200).json()
            .path("lines").size());
    }

    /** 下级行的上级主键指向上级行。 */
    void assertParent(JsonNode parent, JsonNode child) {
        assertEquals(parent.path("chartAccountId").asString(), child.path("parentChartAccountId").asString());
    }

    /** 断言一行的级次、类型、方向与四个金额。 */
    void assertLine(JsonNode line, int level, boolean postingAllowed, NormalSide normalSide, String opening,
        String debit, String credit, String closing) {
        assertAll(line.path("accountNo").asString(), () -> assertEquals(level, line.path("level").asInt()),
            () -> assertEquals(postingAllowed, line.path("postingAllowed").asBoolean()),
            () -> assertEquals(normalSide.getCode(), line.path("normalSide").asString()),
            () -> assertEquals(opening, line.path("openingBalance").asString()),
            () -> assertEquals(debit, line.path("periodDebit").asString()),
            () -> assertEquals(credit, line.path("periodCredit").asString()),
            () -> assertEquals(closing, line.path("closingBalance").asString()));
    }

    /** 一级科目期末余额按实际借贷方向拆分后借贷合计相等。 */
    void assertLevelOneBalanced(List<JsonNode> lines) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (JsonNode line : lines.stream().filter(line -> line.path("level").asInt() == 1).toList()) {
            BigDecimal closing = new BigDecimal(line.path("closingBalance").asString());
            boolean debitNormal = NormalSide.DEBIT.getCode().equals(line.path("normalSide").asString());
            boolean debitSide = debitNormal == (closing.signum() >= 0);
            if (debitSide) {
                debit = debit.add(closing.abs());
            } else {
                credit = credit.add(closing.abs());
            }
        }
        assertEquals(0, debit.compareTo(credit), "一级科目借贷合计应相等: 借 " + debit + " 贷 " + credit);
    }

    /** 科目余额路径。 */
    String path(long ledgerBookId, long periodId) {
        return "/ledger-books/v1/" + ledgerBookId + "/periods/" + periodId + "/account-balances";
    }
}
