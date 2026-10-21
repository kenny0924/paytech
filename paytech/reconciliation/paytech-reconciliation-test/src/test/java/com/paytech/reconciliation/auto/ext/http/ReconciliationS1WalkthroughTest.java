package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 设计稿第 3 节 S1 全程（AC-03、AC-08–AC-12）：PAYMENT 100（预估费 0.50、实扣 0.45）与 REFUND 30 → 渠道账单含 2.00 月费调整行 →
 * 经处置接口归类 CHANNEL_FEE_OTHER（Maker + Checker）→ 对平净额 67.55 → 银行到账 67.40 无建议 → 人工匹配 → 开 BANK_AMOUNT_MISMATCH
 * 0.15 → 归类 BANK_FEE → CASH_CONFIRMED；在途汇总随之变化
 *
 * @see com.paytech.reconciliation.api.cash.CashAccountApi#reviewCashAccount(String, String, String, ReviewCashAccountRequestBody)
 * @see com.paytech.reconciliation.api.exception.ReconExceptionApi#submitReconResolution(String, String, String, com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody)
 * @see com.paytech.reconciliation.api.exception.ReconResolutionApi#reviewReconResolution(String, String, String, com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody)
 * @see com.paytech.reconciliation.api.bank.BankMatchApi#createBankMatches(String, String, com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody)
 * @see com.paytech.reconciliation.api.summary.ReconSummaryApi#getReconSummary(String, String)
 */
class ReconciliationS1WalkthroughTest extends AbstractBankReconciliationTest {
    /** 汇总查询路径。 */
    static final String SUMMARY = "/reconciliation/v1/summary?entityNo=" + ReconciliationTestConstants.ENTITY_NO
        + "&currency=" + ReconciliationTestConstants.CURRENCY;

    /** S1 全程。 */
    @Test
    void test_s1Walkthrough() {
        CashAccount cash = approvedCashAccount();
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().settlementCashAccount(cash.getId())
            .with(item -> item.setCostToleranceAmount(new BigDecimal("0.10"))).build());
        recorded(BillingFactBuilder.payment(account.getId(), "S1-PAY", "100.00").channelCost("0.50").build());
        recorded(BillingFactBuilder.refund(account.getId(), "S1-REF", "30.00").build());

        // 渠道账单：月费调整行经处置接口归类后对平
        long statementId = importStatement(StatementImportBuilder.of(account, "S1").payment("S1-PAY", "100.00", "0.45")
            .refund("S1-REF", "30.00", "0").adjustment(SettlementDirection.DEBIT, "2.00"));
        long resolutionId = classifyAdjustment(statementId);
        assertStatementStatus(statementId, ChannelStatementStatus.RECONCILED);
        ExpectedBankMovement movement = expectedMovements.findBySource(ExpectedMovementType.CHANNEL_SETTLEMENT,
            statementId);
        assertReconciledEvents(events.await(2), resolutionId, movement);
        assertSummary(1, "67.55", 0, "0.00");

        // 银行到账 67.40：金额不等没有建议，人工匹配
        long bankStatementId = importedBank(BankStatementImportBuilder.of(cash, "BANK-S1").opening("1000.00")
            .line("BNK-6740", SettlementDirection.CREDIT, "67.40"));
        BankStatementLine line = bankStatementLines.listByStatement(bankStatementId).getFirst();
        assertEquals(List.of(), bankMatches.listByLine(line.getId(), null));
        assertSummary(1, "67.55", 1, "67.40");
        postAsMaker(BANK_MATCHES, BankMatchApiTest.matches(line, BankMatchApiTest.alloc(movement, "67.40")))
            .assertSuccess(201);
        awaitIdle();
        assertSettlementEvent(events.await(1).getFirst(), movement, line);
        assertEquals(ExpectedMovementStatus.PARTIALLY_CONFIRMED, expectedMovements.findById(movement.getId()).getStatus());
        assertSummary(1, "0.15", 0, "0.00");

        // 差 0.15：开 BANK_AMOUNT_MISMATCH，归类 BANK_FEE
        ReconciliationResponseChecker opened = openException(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT,
            movement.getId()).assertSuccess(201).assertText("differenceAmount", "0.15");
        long bankResolutionId = resolve(opened.id("exceptionId"), classify(ReconClassification.BANK_FEE, "0.15"));
        ExpectedBankMovement confirmed = expectedMovements.findById(movement.getId());
        assertAll(() -> assertEquals(ExpectedMovementStatus.CONFIRMED, confirmed.getStatus()),
            () -> assertEquals(0, new BigDecimal("0.15").compareTo(confirmed.getClassifiedAmount())),
            () -> assertStatementStatus(statementId, ChannelStatementStatus.CASH_CONFIRMED),
            () -> assertEquals(0, new BigDecimal("67.55").compareTo(channelStatements.findById(statementId)
                .getCashConfirmedAmount())),
            () -> assertExceptionStatus(opened.id("exceptionId"), ReconExceptionStatus.RESOLVED));
        ReconAdjustmentApprovedPayload adjustment = events.payload(events.await(1).getFirst(),
            ReconAdjustmentApprovedPayload.class);
        assertAll(() -> assertEquals(bankResolutionId, adjustment.getResolutionId()),
            () -> assertEquals(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT.getCode(), adjustment.getSourceType()),
            () -> assertEquals(ReconClassification.BANK_FEE.getCode(), adjustment.getClassificationCode()),
            () -> assertEquals(0, new BigDecimal("0.15").compareTo(adjustment.getAmount())),
            () -> assertEquals(cash.getId(), adjustment.getCashAccountId()));
        assertSummary(0, "0.00", 0, "0.00");
    }

    /** 经接口新建并由复核人批准的银行账户（账号末四位与对账单头一致）。 */
    CashAccount approvedCashAccount() {
        vault.expectProtect(CashAccountApiTest.DATA_TYPE, CashAccountApiTest.PURPOSE, CashAccountApiTest.TOKEN);
        ReconciliationResponseChecker created = postAsMaker(CASH_ACCOUNTS, CashAccountApiTest.createBody())
            .assertSuccess(201);
        ReviewCashAccountRequestBody review = CashAccountApiTest.reviewBody(CheckerDecision.APPROVED,
            created.text("contentHash"));
        postAsChecker(CASH_ACCOUNTS + "/" + created.id("cashAccountId") + "/approval", review).assertSuccess(200);
        return cashAccounts.findById(created.id("cashAccountId"));
    }

    /** 调整行的 UNEXPLAINED_ADJUSTMENT 按详情给出的归类码与差异金额经处置接口归类。 */
    long classifyAdjustment(long statementId) {
        ReconException exception = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line(statementId, 3).getId(), ReconExceptionType.UNEXPLAINED_ADJUSTMENT);
        JsonNode detail = get(EXCEPTIONS + "/" + exception.getId()).assertSuccess(200).json();
        assertEquals(List.of(ReconClassification.CHANNEL_FEE_OTHER.getCode(), ReconClassification.CHANNEL_RESERVE_HOLD
            .getCode(), ReconClassification.CHANNEL_RESERVE_RELEASE.getCode(), ReconClassification.RECON_DIFFERENCE
            .getCode()), ReconResolutionApiTest.texts(detail.path("availableClassificationCodes")));
        return resolve(exception.getId(), classify(ReconClassification.CHANNEL_FEE_OTHER,
            detail.path("exception").path("differenceAmount").asString()));
    }

    /** 对平事件：成本确认 + 账单对平（带已归类调整行 −2.00 与处置 ID）；渠道账单行的归类不单独发对账调整。 */
    void assertReconciledEvents(List<DomainEvent> published, long resolutionId, ExpectedBankMovement movement) {
        assertEquals(ActualChannelCostConfirmedPayload.EVENT_TYPE, published.get(0).eventType());
        ChannelBatchReconciledPayload batch = events.payload(published.get(1), ChannelBatchReconciledPayload.class);
        ChannelBatchReconciledPayload.ClassifiedLine classified = batch.getClassifiedLines().getFirst();
        assertAll(() -> assertEquals(0, new BigDecimal("67.55").compareTo(batch.getNetAmount())),
            () -> assertEquals(1, batch.getClassifiedLines().size()), () -> assertEquals(3, classified.getLineNo()),
            () -> assertEquals(ReconClassification.CHANNEL_FEE_OTHER.getCode(), classified.getClassificationCode()),
            () -> assertEquals(0, new BigDecimal("-2.00").compareTo(classified.getAmount())),
            () -> assertEquals(resolutionId, classified.getResolutionId()),
            () -> assertEquals(movement.getId(), batch.getExpectedMovementId()));
    }

    /** 到账事件。 */
    void assertSettlementEvent(DomainEvent event, ExpectedBankMovement movement, BankStatementLine line) {
        ChannelSettlementConfirmedPayload payload = events.payload(event, ChannelSettlementConfirmedPayload.class);
        assertAll(() -> assertEquals(movement.getId(), payload.getExpectedMovementId()),
            () -> assertEquals(line.getId(), payload.getBankStatementLineId()),
            () -> assertEquals(0, new BigDecimal("67.40").compareTo(payload.getMatchedAmount())));
    }

    /** 在途汇总：已对平待到账账单数与未确认金额、未匹配贷方流水条数与金额；本例结束前没有开放异常以外的账单。 */
    void assertSummary(int reconciledCount, String unconfirmed, int unmatchedCount, String unmatchedCredit) {
        JsonNode summary = get(SUMMARY).assertSuccess(200).json();
        assertAll(() -> assertEquals(reconciledCount, summary.path("inTransitStatements").path("reconciledCount").asInt()),
            () -> assertEquals(unconfirmed, summary.path("inTransitStatements").path("reconciledUnconfirmedAmount")
                .asString()),
            () -> assertEquals(0, summary.path("inTransitStatements").path("matchingCount").asInt()),
            () -> assertEquals(4, summary.path("openExceptionsByAge").size()),
            () -> assertEquals(0, summary.path("openExceptionsByAge").get(0).path("count").asInt()),
            () -> assertEquals(unmatchedCount, summary.path("unmatchedBankLines").path("count").asInt()),
            () -> assertEquals(unmatchedCredit, summary.path("unmatchedBankLines").path("creditAmount").asString()));
    }
}
