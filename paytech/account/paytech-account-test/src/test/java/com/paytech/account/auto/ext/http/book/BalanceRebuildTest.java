package com.paytech.account.auto.ext.http.book;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.request.RebuildBookBalancesRequestBody;
import com.paytech.account.api.response.BalanceRebuildResponseBody;
import com.paytech.account.app.ext.job.BalanceReconciliationJobHandler;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.builder.EconomicFactBuilder;
import com.paytech.account.test.checker.AccountResponseChecker;
import com.paytech.account.test.mocker.external.LedgerBookMocker;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * 账簿余额重建（design/04 §4 第 5 行，{@code POST /balances/v1/books/{bookId}/rebuild}）：事务外经 ledger-api 读账簿、经 billing-api 分页重放
 * 账簿主体的全部已批准事实，短事务内重写余额与处理记录
 * <li>重建后的余额等于 ledger 按分录聚合的余额（以 ledger account-totals DTO 核对，核对任务无差异）。</li>
 * <li>Finance Entity 账簿不保存 Owner 余额，拒绝重建；重放范围之后已有事实被处理时以并发冲突失败；依赖不可用时 503。</li>
 *
 * @see com.paytech.account.api.BalanceBookApi#rebuildBookBalances(String, RebuildBookBalancesRequestBody)
 */
class BalanceRebuildTest extends AbstractBalanceTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    @Autowired
    private BalanceReconciliationJobHandler reconciliationJob;

    /** 已消费的事实被人为篡改、另有一条事实的事件丢失：重建后余额等于全部事实之和，且与 ledger 分录聚合值一致。 */
    @Test
    void test_rebuildRecomputesFromBillingFacts() {
        EconomicFactPayload first = payment("100").component(AmountSource.CLIENT_PRINCIPAL, "95")
            .component(AmountSource.CHANNEL_COST, "2").build();
        consumed(first);
        testMapper.updateNetBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT.getCode(), CLIENT, "999");
        EconomicFactPayload missed = adjustment().line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, "5").build();
        billingFacts.mockFacts("OPERATING_ENTITY", ENTITY_NO, CURRENCY,
            List.of(EconomicFactBuilder.responseBody(first, EconomicFactStatus.APPROVED),
                EconomicFactBuilder.responseBody(missed, EconomicFactStatus.APPROVED)));

        var result = rebuild(OE_BOOK_ID).assertSuccess(200).as(BalanceRebuildResponseBody.class);

        assertAll(() -> assertEquals("2", result.getReplayedFactCount()),
            () -> assertEquals(missed.getFactId().toString(), result.getMaxFactId()),
            () -> assertEquals("3", result.getBalanceCount()),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(missed.getFactId())));
        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "0", "100",
            "100");
        assertBalance(StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, OwnerType.CHANNEL_ACCOUNT,
            String.valueOf(CHANNEL_ACCOUNT_ID), BalanceBucket.NONE, "100", "0", "100");
        ledger.mockAccountTotals(OE_BOOK_ID, List.of(
            LedgerBookMocker.total(1, StandardAccountCodes.CHANNEL_FEE_ACCRUAL, "CHANNEL_ACCOUNT",
                String.valueOf(CHANNEL_ACCOUNT_ID), "NONE", "CREDIT", "0.00", "2.00", "2.00"),
            LedgerBookMocker.total(2, StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, "CHANNEL_ACCOUNT",
                String.valueOf(CHANNEL_ACCOUNT_ID), "NONE", "DEBIT", "100.00", "0.00", "100.00"),
            LedgerBookMocker.total(3, StandardAccountCodes.CLIENT_PAYABLE, "CLIENT", CLIENT, "PENDING", "CREDIT", "0.00",
                "100.00", "100.00")));
        assertEquals(0, reconciliationJob.runOnce(), "重建后余额与 ledger 分录聚合值一致");
    }

    /** 被替代的事实与替代事实一起重放：余额只反映替代事实。 */
    @Test
    void test_rebuildReplaysSupersededFacts() {
        EconomicFactPayload v1 = adjustment().line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, "10").build();
        EconomicFactPayload v2 = adjustment().line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, "12").supersedes(v1.getFactId(), 2)
            .build();
        billingFacts.mockFacts("OPERATING_ENTITY", ENTITY_NO, CURRENCY,
            List.of(EconomicFactBuilder.responseBody(v1, EconomicFactStatus.SUPERSEDED),
                EconomicFactBuilder.responseBody(v2, EconomicFactStatus.APPROVED)));

        rebuild(OE_BOOK_ID).assertSuccess(200).assertText("replayedFactCount", "2");

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "10", "22",
            "12");
        assertEquals(AppliedFactStatus.REVERSED.getCode(), applyStatus(v1.getFactId()));
    }

    /** Finance Entity 账簿拒绝；重放范围之后已有事实被处理时 409；ledger 不可用时 503。 */
    @Test
    void test_rebuildRejectedOrFailsClosed() {
        rebuild(FE_BOOK_ID).assertBusinessRejected("Owner 余额只在经营主体账簿维护");

        EconomicFactPayload early = payment("10").component(AmountSource.CLIENT_PRINCIPAL, "10").build();
        EconomicFactPayload late = payment("20").component(AmountSource.CLIENT_PRINCIPAL, "20").build();
        consumed(late);
        billingFacts.mockFacts("OPERATING_ENTITY", ENTITY_NO, CURRENCY,
            List.of(EconomicFactBuilder.responseBody(early, EconomicFactStatus.APPROVED)));
        rebuild(OE_BOOK_ID).assertConcurrentRequest();
        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "0", "20", "20");

        ledger.mockUnavailable();
        rebuild(OE_BOOK_ID).assertServiceUnavailable();
    }

    /** 发起重建。 */
    AccountResponseChecker rebuild(long bookId) {
        RebuildBookBalancesRequestBody body = new RebuildBookBalancesRequestBody();
        body.setOperatorId("90001");
        return post("/balances/v1/books/" + bookId + "/rebuild", body);
    }
}
