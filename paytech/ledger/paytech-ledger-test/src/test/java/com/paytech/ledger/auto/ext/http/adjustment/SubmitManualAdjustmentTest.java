package com.paytech.ledger.auto.ext.http.adjustment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.enums.LedgerOwnerType;
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
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 提交人工调整：冻结摘要与 Maker 记录（编制人取自操作人 Header），以及操作人 Header、账簿、期间、账户、币种、精度和平衡的拒绝场景。
 *
 * @see ManualAdjustmentApi#submitManualAdjustment(String, String, SubmitManualAdjustmentRequestBody)
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

    /** 影响 Owner 余额的调整必须经 billing 登记：调整行涉及 Owner 控制科目（Client 应付、Partner 应付等）即以专用错误码拒绝，纯科目重分类不受影响。 */
    @Test
    void test_ownerAccountAdjustmentForbidden() {
        long client = ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);
        long partner = ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.PARTNER_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);

        submit(adjustment().transfer(bankCashAccountId, client, "10")).assertRejectedWithCode(
            LedgerErrorCode.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN.getCode(), "MANUAL_ADJUSTMENT 计费事实");
        submit(adjustment().transfer(partner, liabilityAccountId, "10")).assertRejectedWithCode(
            LedgerErrorCode.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN.getCode(), "账户 [" + partner + "]");
        submitted(adjustment().transfer(liabilityAccountId, capitalAccountId, "10"));
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 0);
    }

    @Test
    void test_unbalancedOrNonPositiveAmountRejected() {
        submit(adjustment().line(bankCashAccountId, EntrySide.DEBIT, "100")
            .line(capitalAccountId, EntrySide.CREDIT, "99.99")).assertRejected(LedgerErrorCode.ADJUSTMENT_UNBALANCED);
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "0"))
            .assertBusinessRejected("调整金额必须大于零");
    }

    @Test
    void test_amountBeyondCurrencyPrecisionRejected() {
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "1.001"))
            .assertBusinessRejected("不超过币种精度 2 位");
    }

    @Test
    void test_accountMustBelongToBookAndBeActive() {
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        long foreignAccount = ledgerMocker.mockAccount(otherBook, "CAPITAL", LedgerOwnerType.OPERATING_ENTITY, "AE002",
            BalanceBucket.NONE);
        submit(adjustment().transfer(bankCashAccountId, foreignAccount, "10"))
            .assertRejected(LedgerErrorCode.ADJUSTMENT_ACCOUNT_INVALID);

        testMapper.updateAccountStatus(capitalAccountId, AccountStatus.INACTIVE.getCode());
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "10"))
            .assertRejected(LedgerErrorCode.ADJUSTMENT_ACCOUNT_INVALID);
    }

    @Test
    void test_periodMustBeOpenAndBelongToBookEntity() {
        LocalDate start = LocalDate.of(2099, 1, 1);
        long futurePeriod = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 1, start,
            start.plusDays(30), PeriodStatus.FUTURE);
        submit(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), futurePeriod)
            .transfer(bankCashAccountId, capitalAccountId, "10")).assertRejected(LedgerErrorCode.ADJUSTMENT_PERIOD_NOT_OPEN);

        long otherCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE002");
        long otherPeriod = ledgerMocker.mockPeriod(otherCalendar, 2099, 1, start, start.plusDays(30), PeriodStatus.OPEN);
        submit(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), otherPeriod)
            .transfer(bankCashAccountId, capitalAccountId, "10")).assertBusinessRejected("会计期间不属于账簿主体");
    }

    @Test
    void test_currencyMustMatchBook() {
        submit(adjustment().currency("AED").transfer(bankCashAccountId, capitalAccountId, "10"))
            .assertBusinessRejected("调整币种与账簿币种不一致");
    }

    /** 编制人取自 X-Gateway-Actor-Subject：缺失、非正十进制或缺少会话 Header 时 400，不落库。 */
    @Test
    void test_invalidActorHeaderRejected() {
        SubmitManualAdjustmentRequestBody body = adjustment().transfer(bankCashAccountId, capitalAccountId, "10").build();

        post(PATH, body).assertInvalidRequest();
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "10"), "maker-1").assertInvalidRequest();
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "10"), "01").assertInvalidRequest();
        HttpHeaders actorOnly = TestRequestHeaders.internal(REQUEST_ID);
        actorOnly.set(ACTOR_HEADER, SubmitManualAdjustmentRequestBuilder.MAKER_ID);
        new LedgerResponseChecker(http.post(PATH, actorOnly, body), objectMapper, REQUEST_ID).assertInvalidRequest();

        assertEquals(0, testMapper.countAdjustments());
    }

    @Test
    void test_invalidParameter() {
        submit(adjustment().line(bankCashAccountId, EntrySide.DEBIT, "10"))
            .assertProblem(422, "VALIDATION_ERROR").assertFieldError("lines");
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "10").file("990001", null))
            .assertInvalidRequest().assertDetailContains("证据文件主键与文件内容摘要必须同时提供或同时为空");
        submit(adjustment().transfer(bankCashAccountId, capitalAccountId, "1e3"))
            .assertProblem(422, "VALIDATION_ERROR");
    }
}
