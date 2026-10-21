package com.paytech.reconciliation.auto.ext.compensation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 逐行匹配规则（AC-06，设计 F1 规则 1–7）：金额超出、多条命中、跨账单重复、不带交易号与无对应交易行的费用行、等待中的行在新平台流水到达后重试
 *
 * @see com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 * @see com.paytech.reconciliation.domainservice.service.ChannelReconciliationResolutionService#releaseLineForRematching(Long)
 */
class ChannelStatementMatchRulesTest extends AbstractReconciliationFlowTest {

    /** 规则 3：行金额超过平台流水剩余 → AMOUNT_MISMATCH，差异为该行净额贡献，平台流水不变。 */
    @Test
    void test_amountOver() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "OVER-1", "50.00", "0").build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-OVER").payment("OVER-1", "80.00", "0"));

        ChannelStatementLine line = line(statementId, 1);
        ReconException mismatch = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, line.getId(),
            ReconExceptionType.AMOUNT_MISMATCH);
        assertAll(() -> assertEquals(LineMatchStatus.EXCEPTION, line.getMatchStatus()),
            () -> assertEquals(0, new BigDecimal("80.00").compareTo(mismatch.getDifferenceAmount())),
            () -> assertTrue(mismatch.getDetail().contains("剩余 50")),
            () -> assertEquals(PlatformTxnStatus.OPEN, platformTxns.findById(payment.getId()).getStatus()));
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
    }

    /** 规则 5：同交易号命中多条可匹配平台流水 → AMBIGUOUS_REFERENCE，说明列出命中 ID，不猜测。 */
    @Test
    void test_ambiguousReference() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        PlatformTxn receivable = data.platformTxn(PlatformTxnBuilder.payment(account, "AMB-1", "10.00", "0").build());
        PlatformTxn payable = data.platformTxn(PlatformTxnBuilder.refund(account, "AMB-1", "10.00")
            .with(txn -> txn.setReferenceType(ReconciliationTestConstants.PAY_REFERENCE_TYPE)).build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-AMB").payment("AMB-1", "10.00", "0"));

        ReconException ambiguous = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line(statementId, 1).getId(), ReconExceptionType.AMBIGUOUS_REFERENCE);
        assertAll(() -> assertTrue(ambiguous.getDetail().contains(String.valueOf(receivable.getId()))),
            () -> assertTrue(ambiguous.getDetail().contains(String.valueOf(payable.getId()))),
            () -> assertEquals(PlatformTxnStatus.OPEN, platformTxns.findById(receivable.getId()).getStatus()));
    }

    /** 规则 6：更早账单已有相同交易行 → DUPLICATE_SETTLEMENT；确认不是重复（ACCEPT）后交还匹配，不再查重。 */
    @Test
    void test_crossStatementDuplicate() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        data.platformTxn(PlatformTxnBuilder.payment(account, "DUP-1", "100.00", "0").build());
        long first = importStatement(StatementImportBuilder.of(account, "BATCH-D1").payment("DUP-1", "100.00", "0"));
        assertEquals(LineMatchStatus.MATCHED, line(first, 1).getMatchStatus());

        long second = importStatement(StatementImportBuilder.of(account, "BATCH-D2").payment("DUP-1", "100.00", "0"));

        ChannelStatementLine duplicated = line(second, 1);
        ReconException duplicate = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, duplicated.getId(),
            ReconExceptionType.DUPLICATE_SETTLEMENT);
        assertAll(() -> assertEquals(LineMatchStatus.EXCEPTION, duplicated.getMatchStatus()),
            () -> assertTrue(duplicate.getBlocking()),
            () -> assertEquals(0, new BigDecimal("100.00").compareTo(duplicate.getDifferenceAmount())));

        acceptDuplicate(duplicate, duplicated.getId());

        assertAll(() -> assertEquals(LineMatchStatus.UNMATCHED, line(second, 1).getMatchStatus()),
            () -> assertExceptionStatus(duplicate.getId(), ReconExceptionStatus.RESOLVED),
            () -> assertNull(activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, duplicated.getId(),
                ReconExceptionType.DUPLICATE_SETTLEMENT)));
    }

    /** 规则 7：不带交易号的 FEE 行与本账单无对应交易行的带交易号 FEE 行 → UNEXPLAINED_ADJUSTMENT，差异为负的费用金额。 */
    @Test
    void test_feeLinesWithoutOwner() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());

        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-FEES").payment("FX-1", "10.00", "0")
            .fee("0.20", null, null).fee("0.30", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "OTHER-1"));

        assertAll(() -> assertUnexplained(statementId, 2, "-0.20"), () -> assertUnexplained(statementId, 3, "-0.30"),
            () -> assertEquals(LineMatchStatus.UNMATCHED, line(statementId, 1).getMatchStatus()));
    }

    /** 规则 4：未命中的行等待；同交易号的平台流水到达后重试并匹配，账单对平。 */
    @Test
    void test_waitingLineRetriedWhenPlatformTxnArrives() {
        ClearingAccount account = clearingAccountWithCash("0");
        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-LATE").payment("LATE-1", "25.00", "0"));
        assertEquals(LineMatchStatus.UNMATCHED, line(statementId, 1).getMatchStatus());
        assertEquals(0, reconExceptions.listActiveByStatement(statementId).size());

        PlatformTxn payment = recorded(BillingFactBuilder.payment(account.getId(), "LATE-1", "25.00").build());
        awaitIdle();

        assertAll(() -> assertEquals(payment.getId(), line(statementId, 1).getPlatformTxnId()),
            () -> assertEquals(PlatformTxnStatus.SETTLED, platformTxns.findById(payment.getId()).getStatus()));
        assertStatementStatus(statementId, ChannelStatementStatus.RECONCILED);
        assertEquals(1, events.await(1).size());
    }

    /** 扮演 C 线批准 ACCEPT_TIMING_DIFFERENCE：异常结案、交还匹配并登记门禁重算。 */
    void acceptDuplicate(ReconException duplicate, long lineId) {
        ReconResolution resolution = resolution(duplicate, ResolutionType.ACCEPT_TIMING_DIFFERENCE, null);
        transactionTemplate.executeWithoutResult(status -> {
            reconResolutions.insert(resolution);
            reconExceptionService.markUnderReview(duplicate);
            reconExceptionService.resolve(duplicate);
            resolutionService.releaseLineForRematching(lineId);
            resolutionService.scheduleStatementEvaluation(duplicate.getStatementId(), resolution.getId());
        });
        awaitIdle();
    }

    /** 某行挂未结 UNEXPLAINED_ADJUSTMENT 且差异为给定金额。 */
    void assertUnexplained(long statementId, int lineNo, String difference) {
        ChannelStatementLine line = line(statementId, lineNo);
        ReconException exception = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, line.getId(),
            ReconExceptionType.UNEXPLAINED_ADJUSTMENT);
        assertAll(() -> assertEquals(LineMatchStatus.EXCEPTION, line.getMatchStatus()),
            () -> assertEquals(0, new BigDecimal(difference).compareTo(exception.getDifferenceAmount())));
    }
}
