package com.paytech.reconciliation.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.app.ext.job.ChannelLineWaitExpiryJobHandler;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.service.processor.response.ExpireWaitingChannelLinesResponse;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 账单行等待期满任务（AC-13，需求 Q-04）：等待期满仍未匹配的交易行转为 CHANNEL_LINE_UNMATCHED，重复执行不重复开异常；平台流水后到时匹配任务
 * 重试该行并自动结案
 *
 * @see ChannelLineWaitExpiryJobHandler#expire()
 * @see ChannelLineWaitExpiryJobHandler#runOnce()
 */
class ChannelLineWaitExpiryJobTest extends AbstractReconciliationFlowTest {
    @Autowired
    private ChannelLineWaitExpiryJobHandler job;

    /** 只处理 MATCHING 账单中超过等待天数的交易行；重复执行幂等；后到的平台流水使异常自动结案。 */
    @Test
    void test_expireWaitingLines() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement matching = ChannelStatementBuilder.matching(account, "BATCH-WAIT", "80.00", "0", "80.00",
            CashDirection.CREDIT, 2).build();
        ChannelStatementLine expired = waitingLine(matching, 1, "OLD-1", "50.00", 4);
        ChannelStatementLine fresh = waitingLine(matching, 2, "NEW-1", "30.00", 0);
        data.channelStatement(matching, List.of(expired, fresh));
        ChannelStatement voided = ChannelStatementBuilder.matching(account, "BATCH-VOIDED", "5.00", "0", "5.00",
            CashDirection.CREDIT, 1).with(item -> item.setStatus(ChannelStatementStatus.VOIDED)).build();
        data.channelStatement(voided, List.of(waitingLine(voided, 1, "OLD-2", "5.00", 10)));

        ExpireWaitingChannelLinesResponse first = job.runOnce();
        ExpireWaitingChannelLinesResponse second = job.runOnce();

        ReconException unmatched = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, expired.getId(),
            ReconExceptionType.CHANNEL_LINE_UNMATCHED);
        assertAll(() -> assertEquals(1, first.getOpenedCount()), () -> assertEquals(0, second.getOpenedCount()),
            () -> assertEquals(LineMatchStatus.EXCEPTION, line(matching.getId(), 1).getMatchStatus()),
            () -> assertEquals(LineMatchStatus.UNMATCHED, line(matching.getId(), 2).getMatchStatus()),
            () -> assertEquals(LineMatchStatus.UNMATCHED, line(voided.getId(), 1).getMatchStatus()),
            () -> assertEquals(0, new BigDecimal("50.00").compareTo(unmatched.getDifferenceAmount())),
            () -> assertEquals(matching.getId(), unmatched.getStatementId()),
            () -> assertEquals(1, reconExceptions.countPage(null, ReconExceptionType.CHANNEL_LINE_UNMATCHED, null, null,
                null, null)));

        PlatformTxn payment = recorded(BillingFactBuilder.payment(account.getId(), "OLD-1", "50.00").build());
        awaitIdle();

        assertAll(() -> assertEquals(payment.getId(), line(matching.getId(), 1).getPlatformTxnId()),
            () -> assertExceptionStatus(unmatched.getId(), ReconExceptionStatus.AUTO_CLOSED),
            () -> assertNull(activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, expired.getId(),
                ReconExceptionType.CHANNEL_LINE_UNMATCHED)));
        job.expire();
        assertEquals(1, reconExceptions.countPage(null, ReconExceptionType.CHANNEL_LINE_UNMATCHED, null, null, null, null));
    }

    /** 已接收若干天、仍未匹配的 PAYMENT 行。 */
    static ChannelStatementLine waitingLine(ChannelStatement statement, int lineNo, String externalTransactionId,
        String amount, int ageDays) {
        ChannelStatementLine line = ChannelStatementBuilder.line(statement, lineNo, ChannelLineType.PAYMENT,
            SettlementDirection.CREDIT, amount, "0", ReconciliationTestConstants.PAY_REFERENCE_TYPE,
            externalTransactionId);
        line.setCreateTime(Instant.now().minus(ageDays, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS));
        return line;
    }
}
