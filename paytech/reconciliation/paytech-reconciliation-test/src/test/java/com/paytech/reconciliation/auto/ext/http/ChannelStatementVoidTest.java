package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;

/**
 * 作废未对平的渠道账单（AC-05、AC-07，UC-R08）：释放行匹配与实际费用、平台流水回到未匹配、本账单未结异常自动结案；已对平或有待复核处置不能作废
 *
 * @see com.paytech.reconciliation.api.statement.ChannelStatementApi#voidChannelStatement(String, String, String)
 */
class ChannelStatementVoidTest extends AbstractReconciliationFlowTest {

    /** 作废后行、平台流水与异常全部回退；同批次重新导入后重新匹配，成本偏差重新开立。 */
    @Test
    void test_voidReleasesMatches() {
        ClearingAccount account = clearingAccountWithCash("0");
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "V-1", "100.00", "0.50").build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-VOID").payment("V-1", "100.00", "0.45")
            .adjustment(SettlementDirection.DEBIT, "2.00");
        long statementId = importStatement(statement);
        ReconException deviation = activeException(ReconExceptionSourceType.PLATFORM_TXN, payment.getId(),
            ReconExceptionType.COST_DEVIATION);
        ReconException unexplained = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line(statementId, 2).getId(), ReconExceptionType.UNEXPLAINED_ADJUSTMENT);

        voidStatement(statementId).assertSuccess(200).assertText("status", ChannelStatementStatus.VOIDED.getCode());

        ChannelStatementLine released = line(statementId, 1);
        PlatformTxn rolledBack = platformTxns.findById(payment.getId());
        assertAll(() -> assertEquals(LineMatchStatus.UNMATCHED, released.getMatchStatus()),
            () -> assertNull(released.getPlatformTxnId()), () -> assertNull(released.getActualFee()),
            () -> assertEquals(LineMatchStatus.UNMATCHED, line(statementId, 2).getMatchStatus()),
            () -> assertEquals(PlatformTxnStatus.OPEN, rolledBack.getStatus()),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(rolledBack.getMatchedAmount())),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(rolledBack.getActualFee())),
            () -> assertExceptionStatus(deviation.getId(), ReconExceptionStatus.AUTO_CLOSED),
            () -> assertExceptionStatus(unexplained.getId(), ReconExceptionStatus.AUTO_CLOSED));
        voidStatement(statementId).assertSuccess(200).assertText("status", ChannelStatementStatus.VOIDED.getCode());

        long revision = importStatement(statement);
        assertAll(() -> assertEquals(2, channelStatements.findById(revision).getRevision()),
            () -> assertEquals(payment.getId(), line(revision, 1).getPlatformTxnId()),
            () -> assertNotNull(activeException(ReconExceptionSourceType.PLATFORM_TXN, payment.getId(),
                ReconExceptionType.COST_DEVIATION)));
        events.assertNone();
    }

    /** 有待复核的处置（异常 UNDER_REVIEW）不能作废。 */
    @Test
    void test_voidRejectedUnderReview() {
        ClearingAccount account = clearingAccountWithCash("0");
        long statementId = importStatement(StatementImportBuilder.of(account, "BATCH-REVIEW").payment("R-1", "10.00", "0")
            .adjustment(SettlementDirection.CREDIT, "1.00"));
        ReconException unexplained = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE,
            line(statementId, 2).getId(), ReconExceptionType.UNEXPLAINED_ADJUSTMENT);
        reconExceptionService.markUnderReview(unexplained);

        voidStatement(statementId).assertBusinessRejected("待复核的处置");
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
    }

    /** 已对平的账单不能作废。 */
    @Test
    void test_voidRejectedWhenReconciled() {
        ClearingAccount account = clearingAccountWithCash("0");
        ChannelStatement reconciled = ChannelStatementBuilder.matching(account, "BATCH-REC", "1.00", "0", "1.00",
            CashDirection.CREDIT, 0).with(item -> item.setStatus(ChannelStatementStatus.RECONCILED)).build();
        data.channelStatement(reconciled, List.of());

        voidStatement(reconciled.getId()).assertBusinessRejected("只有接收中或匹配中的渠道账单可以作废");
        voidStatement(999L).assertBusinessRejected("渠道账单不存在");
    }
}
