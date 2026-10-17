package com.paytech.ledger.auto.ext.http.adjustment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 提交人工调整：冻结摘要与 Maker 记录，以及账簿、期间、账户、币种、精度和平衡的拒绝场景。
 *
 * @see ManualAdjustmentApi#submitManualAdjustment(SubmitManualAdjustmentRequestBody)
 */
class SubmitManualAdjustmentTest extends AbstractManualAdjustmentApiTest {
    private static final String FILE_HASH = "a".repeat(64);

    @Test
    void test_normalFlowFreezesContent() {
        var response = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100.5")
            .file("990001", FILE_HASH));

        long id = response.id("manualAdjustmentId");
        ManualAdjustment adjustment = testMapper.selectAdjustment(id);
        assertAll(() -> assertEquals(AccountingCodes.MANUAL_ADJUSTMENT_NO_PREFIX + id, response.text("adjustmentNo")),
            () -> assertEquals(AdjustmentStatus.SUBMITTED.getCode(), response.text("status")),
            () -> assertTrue(response.text("contentHash").matches("[0-9a-f]{64}")),
            () -> assertEquals(response.text("contentHash"), adjustment.getContentHash()),
            () -> assertEquals(0, new BigDecimal("100.50").compareTo(adjustment.getTotalDebit())),
            () -> assertEquals(0, new BigDecimal("100.50").compareTo(adjustment.getTotalCredit())),
            () -> assertEquals(990001L, adjustment.getFileId()), () -> assertEquals(FILE_HASH, adjustment.getFileContentHash()),
            () -> assertEquals(Long.valueOf(SubmitManualAdjustmentRequestBuilder.MAKER_ID), adjustment.getMakerId()),
            () -> assertEquals(1, testMapper.countApprovals(id)));
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 0);
    }

    @Test
    void test_unbalancedOrNonPositiveAmountRejected() {
        post(PATH, adjustment().line(bankCashAccountId, EntrySide.DEBIT, "100")
            .line(capitalAccountId, EntrySide.CREDIT, "99.99").build()).assertBusinessRejected("调整借贷不平衡");
        post(PATH, adjustment().transfer(bankCashAccountId, capitalAccountId, "0").build())
            .assertBusinessRejected("调整金额必须大于零");
    }

    @Test
    void test_amountBeyondCurrencyPrecisionRejected() {
        post(PATH, adjustment().transfer(bankCashAccountId, capitalAccountId, "1.001").build())
            .assertBusinessRejected("不超过币种精度 2 位");
    }

    @Test
    void test_accountMustBelongToBookAndBeActive() {
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        long foreignAccount = ledgerMocker.mockAccount(otherBook, "CAPITAL", OwnerType.OPERATING_ENTITY, "AE002",
            BalanceBucket.NONE);
        post(PATH, adjustment().transfer(bankCashAccountId, foreignAccount, "10").build())
            .assertBusinessRejected("账户不存在或不属于账簿: 账户 " + foreignAccount);

        testMapper.updateAccountStatus(capitalAccountId, AccountStatus.INACTIVE.getCode());
        post(PATH, adjustment().transfer(bankCashAccountId, capitalAccountId, "10").build())
            .assertBusinessRejected("账户未开放过账: " + capitalAccountId);
    }

    @Test
    void test_periodMustBeOpenAndBelongToBookEntity() {
        LocalDate start = LocalDate.of(2099, 1, 1);
        long futurePeriod = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 1, start,
            start.plusDays(30), PeriodStatus.FUTURE);
        post(PATH, SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), futurePeriod)
            .transfer(bankCashAccountId, capitalAccountId, "10").build()).assertBusinessRejected("会计期间不存在或未开放");

        long otherCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE002");
        long otherPeriod = ledgerMocker.mockPeriod(otherCalendar, 2099, 1, start, start.plusDays(30), PeriodStatus.OPEN);
        post(PATH, SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), otherPeriod)
            .transfer(bankCashAccountId, capitalAccountId, "10").build()).assertBusinessRejected("会计期间不属于账簿主体");
    }

    @Test
    void test_currencyMustMatchBook() {
        post(PATH, adjustment().currency("AED").transfer(bankCashAccountId, capitalAccountId, "10").build())
            .assertBusinessRejected("调整币种与账簿币种不一致");
    }

    @Test
    void test_invalidParameter() {
        post(PATH, adjustment().line(bankCashAccountId, EntrySide.DEBIT, "10").build())
            .assertProblem(422, "VALIDATION_ERROR").assertFieldError("lines");
        post(PATH, adjustment().transfer(bankCashAccountId, capitalAccountId, "10").file("990001", null).build())
            .assertInvalidRequest().assertDetailContains("证据文件主键与文件内容摘要必须同时提供或同时为空");
        post(PATH, adjustment().transfer(bankCashAccountId, capitalAccountId, "1e3").build())
            .assertProblem(422, "VALIDATION_ERROR");
    }
}
