package com.paytech.reconciliation.auto.ext.compensation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 对平门禁、对平与事件（AC-07、AC-08，设计 §3 S1）：完成导入后由真实补偿调度执行 CHANNEL_STATEMENT_MATCH，事件经真实 Broker 观察
 * <li>S1：PAYMENT 100（实扣费 0.45，预估 0.50）、REFUND 30、ADJUSTMENT 月费 2.00 → 调整行 UNEXPLAINED_ADJUSTMENT；归类 CHANNEL_FEE_OTHER 后对平，
 * 净额 67.55 = 100 − 30 − 0.45 − 2.00，发 ActualChannelCostConfirmed 与 ChannelBatchReconciled，生成 CREDIT 67.55 预期银行流水。</li>
 *
 * @see com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 * @see com.paytech.reconciliation.service.handler.ChannelBatchReconciledHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 * @see com.paytech.reconciliation.service.handler.ActualChannelCostConfirmedHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 * @see com.paytech.reconciliation.api.statement.ChannelStatementApi#getChannelStatement(String)
 * @see com.paytech.reconciliation.api.statement.PlatformTxnApi#listPlatformTxns(String, String, String, String, String, String, String, Integer, Integer)
 */
class ChannelStatementReconcileTest extends AbstractReconciliationFlowTest {

    /** 设计 §3 S1 全流程：匹配、调整行异常与门禁、归类后对平、两类事件与预期银行流水。 */
    @Test
    void test_s1Walkthrough() {
        ClearingAccount account = clearingAccountWithCash("0.10");
        PlatformTxn payment = recorded(BillingFactBuilder.payment(account.getId(), "S1-PAY", "100.00")
            .channelCost("0.50").build());
        PlatformTxn refund = recorded(BillingFactBuilder.refund(account.getId(), "S1-REF", "30.00").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "S1").payment("S1-PAY", "100.00", "0.45")
            .refund("S1-REF", "30.00", "0").adjustment(SettlementDirection.DEBIT, "2.00"));

        assertMatchedBeforeClassification(statementId, payment, refund);
        ChannelStatementLine adjustment = line(statementId, 3);
        ReconResolution resolution = classifyLine(adjustment.getId(), ReconExceptionType.UNEXPLAINED_ADJUSTMENT,
            ReconClassification.CHANNEL_FEE_OTHER);

        assertStatementStatus(statementId, ChannelStatementStatus.RECONCILED);
        assertSettled(payment.getId(), "100.00");
        assertSettled(refund.getId(), "30.00");
        ExpectedBankMovement movement = expectedMovements.findBySource(ExpectedMovementType.CHANNEL_SETTLEMENT,
            statementId);
        assertAll(() -> assertEquals(account.getSettlementCashAccountId(), movement.getCashAccountId()),
            () -> assertEquals(SettlementDirection.CREDIT, movement.getDirection()),
            () -> assertEquals(0, new BigDecimal("67.55").compareTo(movement.getExpectedAmount())),
            () -> assertEquals(StatementImportBuilder.settlementDate(), movement.getExpectedDate()),
            () -> assertEquals(ExpectedMovementStatus.OPEN, movement.getStatus()));
        List<DomainEvent> published = events.await(2);
        assertCostEvent(published.get(0), payment, statementId);
        assertBatchEvent(published.get(1), account, statementId, resolution, movement);
        assertEquals(2, get("/reconciliation/v1/platform-txns?statementId=" + statementId).assertSuccess(200)
            .assertPage(1, 10, 2).size());
        var detail = get(STATEMENTS + "/" + statementId).assertSuccess(200);
        assertAll(() -> assertTrue(detail.json().path("gate").isNull() || detail.json().path("gate").isMissingNode()),
            () -> assertEquals(String.valueOf(movement.getId()), detail.text("expectedMovementId")));
    }

    /** 实际费用等于预估：对平只发 ChannelBatchReconciled；带交易号的 FEE 行并入交易行的实际费用。 */
    @Test
    void test_equalFeeNoCostEventAndFoldedFee() {
        ClearingAccount account = clearingAccountWithCash("0");
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "F-1", "100.00", "0.45").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-FEE").payment("F-1", "100.00", "0.30")
            .fee("0.15", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "F-1"));

        assertStatementStatus(statementId, ChannelStatementStatus.RECONCILED);
        assertEquals(0, new BigDecimal("0.45").compareTo(line(statementId, 1).getActualFee()));
        ChannelStatementLine fee = line(statementId, 2);
        assertAll(() -> assertEquals(LineMatchStatus.MATCHED, fee.getMatchStatus()),
            () -> assertNull(fee.getPlatformTxnId()), () -> assertNull(fee.getActualFee()));
        assertSettled(payment.getId(), "100.00");
        ChannelBatchReconciledPayload batch = events.payload(events.await(1).getFirst(),
            ChannelBatchReconciledPayload.class);
        assertAll(() -> assertEquals(0, new BigDecimal("0.45").compareTo(batch.getActualFeeTotal())),
            () -> assertEquals(0, new BigDecimal("99.55").compareTo(batch.getNetAmount())),
            () -> assertEquals(List.of(), batch.getClassifiedLines()));
    }

    /** 费用偏差超过容差：完全匹配时开阻塞 COST_DEVIATION，账单停在 MATCHING。 */
    @Test
    void test_costDeviationBlocks() {
        ClearingAccount account = clearingAccountWithCash("0.01");
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "CD-1", "100.00", "0.50").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-CD").payment("CD-1", "100.00", "0.45"));

        ReconException deviation = activeException(ReconExceptionSourceType.PLATFORM_TXN, payment.getId(),
            ReconExceptionType.COST_DEVIATION);
        assertAll(() -> assertTrue(deviation.getBlocking()), () -> assertEquals(statementId, deviation.getStatementId()),
            () -> assertEquals(0, new BigDecimal("-0.05").compareTo(deviation.getDifferenceAmount())));
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
        JsonNode gate = get(STATEMENTS + "/" + statementId).assertSuccess(200).json().path("gate");
        assertAll(() -> assertTrue(gate.path("transactionLinesMatched").asBoolean()),
            () -> assertFalse(gate.path("noBlockingException").asBoolean()));
        events.assertNone();
    }

    /** 部分匹配：一笔平台流水被两张账单分别覆盖，第一张对平后只部分结清，第二张对平后 SETTLED。 */
    @Test
    void test_partialCoverageAcrossStatements() {
        ClearingAccount account = clearingAccountWithCash("0");
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "PART-1", "100.00", "0").build());

        long first = importStatement(StatementImportBuilder.of(account, "BATCH-P1").payment("PART-1", "60.00", "0"));
        PlatformTxn afterFirst = platformTxns.findById(payment.getId());
        assertAll(() -> assertEquals(PlatformTxnStatus.PARTIALLY_MATCHED, afterFirst.getStatus()),
            () -> assertEquals(0, new BigDecimal("60").compareTo(afterFirst.getSettledAmount())));
        long second = importStatement(StatementImportBuilder.of(account, "BATCH-P2").payment("PART-1", "40.00", "0"));

        assertStatementStatus(first, ChannelStatementStatus.RECONCILED);
        assertStatementStatus(second, ChannelStatementStatus.RECONCILED);
        assertSettled(payment.getId(), "100.00");
        assertEquals(2, events.await(2).size());
    }

    /** 净额为 0（NONE）：不需要到账银行账户，对平直接 CASH_CONFIRMED，不生成预期银行流水。 */
    @Test
    void test_noneNetCashConfirmed() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        data.platformTxn(PlatformTxnBuilder.payment(account, "N-1", "30.00", "0").build());
        data.platformTxn(PlatformTxnBuilder.refund(account, "N-2", "30.00").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-NONE").payment("N-1", "30.00", "0")
            .refund("N-2", "30.00", "0"));

        assertStatementStatus(statementId, ChannelStatementStatus.CASH_CONFIRMED);
        assertNull(expectedMovements.findBySource(ExpectedMovementType.CHANNEL_SETTLEMENT, statementId));
        ChannelBatchReconciledPayload batch = events.payload(events.await(1).getFirst(),
            ChannelBatchReconciledPayload.class);
        assertAll(() -> assertEquals(CashDirection.NONE.getCode(), batch.getCashDirection()),
            () -> assertNull(batch.getExpectedMovementId()), () -> assertEquals(2, batch.getPlatformTxnCount()));
    }

    /** 未配置到账银行账户：其余门禁都满足也不能对平（决定 I-04）。 */
    @Test
    void test_missingCashAccountBlocksReconcile() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        data.platformTxn(PlatformTxnBuilder.payment(account, "C-1", "10.00", "0").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-CASH").payment("C-1", "10.00", "0"));

        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
        JsonNode gate = get(STATEMENTS + "/" + statementId).assertSuccess(200).json().path("gate");
        assertAll(() -> assertTrue(gate.path("transactionLinesMatched").asBoolean()),
            () -> assertTrue(gate.path("adjustmentLinesClassified").asBoolean()),
            () -> assertTrue(gate.path("noBlockingException").asBoolean()),
            () -> assertTrue(gate.path("totalsConsistent").asBoolean()),
            () -> assertFalse(gate.path("settlementCashAccountConfigured").asBoolean()));
        events.assertNone();
    }

    /** 归类前：交易行匹配、调整行开异常、门禁两项未满足、不发事件。 */
    void assertMatchedBeforeClassification(long statementId, PlatformTxn payment, PlatformTxn refund) {
        ChannelStatementLine paymentLine = line(statementId, 1);
        assertAll(() -> assertEquals(payment.getId(), paymentLine.getPlatformTxnId()),
            () -> assertEquals(MatchMethod.AUTO, paymentLine.getMatchMethod()),
            () -> assertEquals(0, new BigDecimal("0.45").compareTo(paymentLine.getActualFee())),
            () -> assertEquals(refund.getId(), line(statementId, 2).getPlatformTxnId()),
            () -> assertEquals(LineMatchStatus.EXCEPTION, line(statementId, 3).getMatchStatus()),
            () -> assertEquals(PlatformTxnStatus.MATCHED, platformTxns.findById(payment.getId()).getStatus()));
        ReconException unexplained = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line(statementId, 3).getId(), ReconExceptionType.UNEXPLAINED_ADJUSTMENT);
        assertAll(() -> assertTrue(unexplained.getBlocking()),
            () -> assertEquals(0, new BigDecimal("-2.00").compareTo(unexplained.getDifferenceAmount())),
            () -> assertNull(activeException(ReconExceptionSourceType.PLATFORM_TXN, payment.getId(),
                ReconExceptionType.COST_DEVIATION)));
        JsonNode gate = get(STATEMENTS + "/" + statementId).assertSuccess(200).json().path("gate");
        assertAll(() -> assertTrue(gate.path("transactionLinesMatched").asBoolean()),
            () -> assertFalse(gate.path("adjustmentLinesClassified").asBoolean()),
            () -> assertFalse(gate.path("noBlockingException").asBoolean()),
            () -> assertTrue(gate.path("totalsConsistent").asBoolean()));
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
        events.assertNone();
    }

    /** 平台流水已结清给定金额。 */
    void assertSettled(long platformTxnId, String amount) {
        PlatformTxn txn = platformTxns.findById(platformTxnId);
        assertAll(() -> assertEquals(PlatformTxnStatus.SETTLED, txn.getStatus()),
            () -> assertEquals(0, new BigDecimal(amount).compareTo(txn.getSettledAmount())));
    }

    /** ActualChannelCostConfirmed：预估 0.50、实际 0.45、差额 −0.05。 */
    void assertCostEvent(DomainEvent event, PlatformTxn payment, long statementId) {
        ActualChannelCostConfirmedPayload cost = events.payload(event, ActualChannelCostConfirmedPayload.class);
        assertAll(() -> assertEquals(ActualChannelCostConfirmedPayload.EVENT_TYPE, event.eventType()),
            () -> assertEquals(String.valueOf(payment.getId()), event.aggregateId()),
            () -> assertEquals(ActualChannelCostConfirmedPayload.AGGREGATE_VERSION, event.aggregateVersion()),
            () -> assertEquals(payment.getBillingFactId(), cost.getBillingFactId()),
            () -> assertEquals(PlatformTxnSource.PAYMENT.getCode(), cost.getTxnSource()),
            () -> assertEquals(0, new BigDecimal("0.50").compareTo(cost.getExpectedFee())),
            () -> assertEquals(0, new BigDecimal("0.45").compareTo(cost.getActualFee())),
            () -> assertEquals(0, new BigDecimal("-0.05").compareTo(cost.getDifference())),
            () -> assertEquals("S1-PAY", cost.getExternalTransactionId()),
            () -> assertEquals(statementId, cost.getStatementId()), () -> assertNotNull(cost.getConfirmedTime()));
    }

    /** ChannelBatchReconciled：合计、已归类调整行与净额恒等式。 */
    void assertBatchEvent(DomainEvent event, ClearingAccount account, long statementId, ReconResolution resolution,
        ExpectedBankMovement movement) {
        ChannelBatchReconciledPayload batch = events.payload(event, ChannelBatchReconciledPayload.class);
        ChannelBatchReconciledPayload.ClassifiedLine classified = batch.getClassifiedLines().getFirst();
        BigDecimal replay = batch.getReceivableTotal().subtract(batch.getPayableTotal())
            .subtract(batch.getActualFeeTotal()).add(classified.getAmount());
        assertAll(() -> assertEquals(ChannelBatchReconciledPayload.EVENT_TYPE, event.eventType()),
            () -> assertEquals(String.valueOf(statementId), event.aggregateId()),
            () -> assertEquals(account.getClearingAccountCode(), batch.getClearingAccountCode()),
            () -> assertEquals(CashDirection.CREDIT.getCode(), batch.getCashDirection()),
            () -> assertEquals(0, new BigDecimal("67.55").compareTo(batch.getNetAmount())),
            () -> assertEquals(0, new BigDecimal("100.00").compareTo(batch.getReceivableTotal())),
            () -> assertEquals(0, new BigDecimal("30.00").compareTo(batch.getPayableTotal())),
            () -> assertEquals(0, new BigDecimal("0.45").compareTo(batch.getActualFeeTotal())),
            () -> assertEquals(1, batch.getClassifiedLines().size()), () -> assertEquals(3, classified.getLineNo()),
            () -> assertEquals(ReconClassification.CHANNEL_FEE_OTHER.getCode(), classified.getClassificationCode()),
            () -> assertEquals(0, new BigDecimal("-2.00").compareTo(classified.getAmount())),
            () -> assertEquals(resolution.getId(), classified.getResolutionId()),
            () -> assertEquals(2, batch.getPlatformTxnCount()),
            () -> assertEquals(movement.getId(), batch.getExpectedMovementId()),
            () -> assertEquals(0, replay.compareTo(batch.getNetAmount())));
    }
}
