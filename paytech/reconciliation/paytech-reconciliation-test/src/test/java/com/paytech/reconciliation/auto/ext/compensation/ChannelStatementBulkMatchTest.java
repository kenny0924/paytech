package com.paytech.reconciliation.auto.ext.compensation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 几百行账单的集合式匹配（缺陷 D-1）：同一张账单混合规则 1–7 的各分支，经真实分段导入与补偿任务 CHANNEL_STATEMENT_MATCH 执行
 * <li>批量加载候选与批量写回不改变逐行规则：完全匹配、同账单多行部分覆盖、带交易号 FEE 行并入、金额超出、多条命中、跨账单重复、
 * 跨账单部分覆盖、等待、调整行、成本偏差；任务重跑时已开异常不重复开立。</li>
 *
 * @see com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler#handle(com.paytech.foundation.compensation.api.CompensationEvent)
 */
class ChannelStatementBulkMatchTest extends AbstractReconciliationFlowTest {
    /** 完全匹配的 PAYMENT 行数。 */
    static final int EXACT = 200;

    /** 每类分支的行数。 */
    static final int EACH = 10;

    /** 混合规则：逐类核对行状态、异常与平台流水进度；随后新平台流水到达触发重跑，只新增匹配，不重复开异常。 */
    @Test
    void test_mixedRulesInOneStatement() {
        ClearingAccount account = clearingAccountWithCash("0.10");
        long earlier = importStatement(earlierStatement(account));
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-BULK");
        Map<String, PlatformTxn> txns = mixedFixture(account, statement);

        long statementId = importStatement(statement);

        Map<String, ChannelStatementLine> lines = linesByReference(statementId);
        assertAll(() -> assertLines(lines, "EX-", LineMatchStatus.MATCHED, EXACT),
            () -> assertLines(lines, "SPLIT-", LineMatchStatus.MATCHED, EACH),
            () -> assertLines(lines, "OVER-", LineMatchStatus.EXCEPTION, EACH),
            () -> assertLines(lines, "AMB-", LineMatchStatus.EXCEPTION, EACH),
            () -> assertLines(lines, "DUP-", LineMatchStatus.EXCEPTION, EACH),
            () -> assertLines(lines, "WAIT-", LineMatchStatus.UNMATCHED, EACH),
            () -> assertLines(lines, "PART-", LineMatchStatus.MATCHED, EACH),
            () -> assertLines(lines, "DEV-", LineMatchStatus.MATCHED, EACH),
            () -> assertLines(lines, "RF-", LineMatchStatus.MATCHED, EACH),
            () -> assertLines(lines, "FOLD-", LineMatchStatus.MATCHED, EACH));
        assertFixtureOutcome(statementId, earlier, txns);

        PlatformTxn late = recorded(BillingFactBuilder.payment(account.getId(), "WAIT-0", "7.00").build());
        awaitIdle();

        assertAll(() -> assertEquals(late.getId(), line(statementId, lineNoOf(statement, "WAIT-0")).getPlatformTxnId()),
            () -> assertEquals(EACH * 4 + 1, reconExceptions.listActiveByStatement(statementId).size()),
            () -> assertEquals(PlatformTxnStatus.MATCHED, platformTxns.findById(late.getId()).getStatus()));
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
    }

    /** 全部可匹配的 600 行：一次任务对平，平台流水全部 SETTLED，只为费用不同的平台流水登记成本确认事件。 */
    @Test
    void test_largeStatementReconciles() {
        ClearingAccount account = clearingAccountWithCash("0.10");
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-LARGE");
        List<PlatformTxn> txns = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            txns.add(data.platformTxn(PlatformTxnBuilder.payment(account, "L-" + i, "10.00", "0.05").build()));
            statement.payment("L-" + i, "10.00", i < 3 ? "0.07" : "0.05");
        }
        for (int i = 0; i < 50; i++) {
            txns.add(data.platformTxn(PlatformTxnBuilder.payment(account, "LS-" + i, "20.00", "0.10").build()));
            statement.payment("LS-" + i, "12.00", "0.06").payment("LS-" + i, "8.00", "0.04");
        }

        long statementId = importStatement(statement);

        assertStatementStatus(statementId, ChannelStatementStatus.RECONCILED);
        assertAll(() -> assertEquals(600, channelStatementLines.countPage(statementId, LineMatchStatus.MATCHED, null)),
            () -> assertTrue(platformTxns.findByIds(txns.stream().map(PlatformTxn::getId).toList()).stream()
                .allMatch(txn -> txn.getStatus() == PlatformTxnStatus.SETTLED)),
            () -> assertEquals(0, new BigDecimal("0.07").compareTo(platformTxns.findById(txns.getFirst().getId())
                .getActualFee())));
        Map<String, Long> byType = events.await(4).stream()
            .collect(Collectors.groupingBy(DomainEvent::eventType, Collectors.counting()));
        assertAll(() -> assertEquals(1L, byType.get(ChannelBatchReconciledPayload.EVENT_TYPE)),
            () -> assertEquals(3L, byType.get(ActualChannelCostConfirmedPayload.EVENT_TYPE)));
    }

    /** 更早的账单：DUP-* 与本账单完全相同（规则 6），PART-* 先覆盖平台流水的 10.00（跨账单部分覆盖）。 */
    StatementImportBuilder earlierStatement(ClearingAccount account) {
        StatementImportBuilder earlier = StatementImportBuilder.of(account, "BATCH-EARLY");
        for (int i = 0; i < EACH; i++) {
            data.platformTxn(PlatformTxnBuilder.payment(account, "DUP-" + i, "9.00", "0").build());
            data.platformTxn(PlatformTxnBuilder.payment(account, "PART-" + i, "30.00", "0").build());
            earlier.payment("DUP-" + i, "9.00", "0").payment("PART-" + i, "10.00", "0");
        }
        return earlier;
    }

    /** 本账单的各类行与平台流水；返回按交易号索引的平台流水（AMB 取应收那条）。 */
    Map<String, PlatformTxn> mixedFixture(ClearingAccount account, StatementImportBuilder statement) {
        for (int i = 0; i < EXACT; i++) {
            data.platformTxn(PlatformTxnBuilder.payment(account, "EX-" + i, "10.00", "0.05").build());
            statement.payment("EX-" + i, "10.00", "0.05");
        }
        for (int i = 0; i < EACH; i++) {
            data.platformTxn(PlatformTxnBuilder.payment(account, "SPLIT-" + i, "20.00", "0.10").build());
            data.platformTxn(PlatformTxnBuilder.payment(account, "OVER-" + i, "5.00", "0").build());
            data.platformTxn(PlatformTxnBuilder.payment(account, "AMB-" + i, "6.00", "0").build());
            data.platformTxn(PlatformTxnBuilder.refund(account, "AMB-" + i, "6.00")
                .with(txn -> txn.setReferenceType(ReconciliationTestConstants.PAY_REFERENCE_TYPE)).build());
            data.platformTxn(PlatformTxnBuilder.payment(account, "DEV-" + i, "10.00", "0.05").build());
            data.platformTxn(PlatformTxnBuilder.refund(account, "RF-" + i, "3.00").build());
            data.platformTxn(PlatformTxnBuilder.payment(account, "FOLD-" + i, "10.00", "0.30").build());
            statement.payment("SPLIT-" + i, "12.00", "0.06").payment("SPLIT-" + i, "8.00", "0.04")
                .payment("OVER-" + i, "8.00", "0").payment("AMB-" + i, "6.00", "0").payment("DUP-" + i, "9.00", "0")
                .payment("WAIT-" + i, "7.00", "0").payment("PART-" + i, "20.00", "0").payment("DEV-" + i, "10.00", "0.50")
                .refund("RF-" + i, "3.00", "0").payment("FOLD-" + i, "10.00", "0.10")
                .fee("0.20", ReconciliationTestConstants.PAY_REFERENCE_TYPE, "FOLD-" + i);
        }
        statement.adjustment(SettlementDirection.DEBIT, "1.00");
        return platformTxns.findPage(account.getId(), null, null, null, null, null, null, 0, 1000).stream()
            .filter(txn -> txn.getDirection() == ClearingDirection.RECEIVABLE
                || !txn.getExternalTransactionId().startsWith("AMB-"))
            .collect(Collectors.toMap(PlatformTxn::getExternalTransactionId, Function.identity()));
    }

    /** 异常与平台流水进度。 */
    void assertFixtureOutcome(long statementId, long earlier, Map<String, PlatformTxn> before) {
        List<ReconException> active = reconExceptions.listActiveByStatement(statementId);
        Map<ReconExceptionType, Integer> byType = active.stream()
            .collect(Collectors.groupingBy(ReconException::getExceptionType, Collectors.summingInt(item -> 1)));
        Map<String, PlatformTxn> after = platformTxns.findByIds(before.values().stream().map(PlatformTxn::getId).toList())
            .stream().collect(Collectors.toMap(PlatformTxn::getExternalTransactionId, Function.identity()));
        assertAll(() -> assertEquals(EACH, byType.get(ReconExceptionType.AMOUNT_MISMATCH)),
            () -> assertEquals(EACH, byType.get(ReconExceptionType.AMBIGUOUS_REFERENCE)),
            () -> assertEquals(EACH, byType.get(ReconExceptionType.DUPLICATE_SETTLEMENT)),
            () -> assertEquals(EACH, byType.get(ReconExceptionType.COST_DEVIATION)),
            () -> assertEquals(1, byType.get(ReconExceptionType.UNEXPLAINED_ADJUSTMENT)),
            () -> assertEquals(5, byType.size()),
            () -> assertEquals(ChannelStatementStatus.RECONCILED, channelStatements.findById(earlier).getStatus()),
            () -> assertProgress(after.get("EX-0"), PlatformTxnStatus.MATCHED, "10.00", "0.05"),
            () -> assertProgress(after.get("SPLIT-0"), PlatformTxnStatus.MATCHED, "20.00", "0.10"),
            () -> assertProgress(after.get("PART-0"), PlatformTxnStatus.MATCHED, "30.00", "0"),
            () -> assertProgress(after.get("DEV-0"), PlatformTxnStatus.MATCHED, "10.00", "0.50"),
            () -> assertProgress(after.get("FOLD-0"), PlatformTxnStatus.MATCHED, "10.00", "0.30"),
            () -> assertProgress(after.get("RF-0"), PlatformTxnStatus.MATCHED, "3.00", "0"),
            () -> assertProgress(after.get("OVER-0"), PlatformTxnStatus.OPEN, "0", "0"),
            () -> assertProgress(after.get("AMB-0"), PlatformTxnStatus.OPEN, "0", "0"),
            () -> assertEquals(PlatformTxnStatus.SETTLED, after.get("DUP-0").getStatus()),
            () -> assertTrue(active.stream().filter(item -> item.getSourceType() == ReconExceptionSourceType.PLATFORM_TXN)
                .allMatch(item -> new BigDecimal("0.45").compareTo(item.getDifferenceAmount()) == 0)));
        assertEquals(EACH, channelStatementLines.findPage(statementId, LineMatchStatus.MATCHED, ChannelLineType.FEE, 0, 100)
            .size());
    }

    /** 平台流水的状态、已匹配金额与实际费用。 */
    static void assertProgress(PlatformTxn txn, PlatformTxnStatus status, String matched, String actualFee) {
        assertAll(txn.getExternalTransactionId(), () -> assertEquals(status, txn.getStatus()),
            () -> assertEquals(0, new BigDecimal(matched).compareTo(txn.getMatchedAmount())),
            () -> assertEquals(0, new BigDecimal(actualFee).compareTo(txn.getActualFee())));
    }

    /** 某前缀的交易行（每个交易号取第一行）全部为给定状态，且数量相符。 */
    static void assertLines(Map<String, ChannelStatementLine> lines, String prefix, LineMatchStatus status, int count) {
        List<ChannelStatementLine> selected = lines.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
            .map(Map.Entry::getValue).toList();
        assertEquals(count, selected.size(), prefix);
        assertTrue(selected.stream().allMatch(line -> line.getMatchStatus() == status), prefix + " " + status);
    }

    /** 本账单交易行按交易号索引（同交易号取行号最小的一行）。 */
    Map<String, ChannelStatementLine> linesByReference(long statementId) {
        return channelStatementLines.listByStatement(statementId).stream()
            .filter(line -> line.getLineType() != ChannelLineType.FEE && line.getExternalTransactionId() != null)
            .collect(Collectors.toMap(ChannelStatementLine::getExternalTransactionId, Function.identity(),
                (first, second) -> first));
    }

    /** 交易号在导入请求中的行号。 */
    static int lineNoOf(StatementImportBuilder statement, String externalTransactionId) {
        return statement.lines().stream().filter(line -> externalTransactionId.equals(line.getExternalTransactionId()))
            .findFirst().orElseThrow().getLineNo();
    }
}
