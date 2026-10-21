package com.paytech.reconciliation.auto.ext.compensation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.service.handler.BankMatchSuggestHandler;
import com.paytech.reconciliation.service.handler.ChannelSettlementConfirmedHandler;
import com.paytech.reconciliation.service.handler.ReconAdjustmentApprovedHandler;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementBuilder;
import com.paytech.reconciliation.test.builder.ExpectedBankMovementBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 银行层本地补偿 Handler 的入口测试：匹配建议规则与身份校验；两个发布 Handler 的终态失败（正常发布在 HTTP 用例中由调度覆盖）
 * <li>建议规则：方向一致、剩余金额相等、起息日与预期日相差不超过 3 个工作日（只排除周末）；同一对不重复建议，已有建议的预期流水不再配对。</li>
 *
 * @see BankMatchSuggestHandler#handle(CompensationEvent)
 * @see ChannelSettlementConfirmedHandler#handle(CompensationEvent)
 * @see ReconAdjustmentApprovedHandler#handle(CompensationEvent)
 */
class BankCompensationHandlerTest extends AbstractBankReconciliationTest {
    /** 预期日：星期一。 */
    static final LocalDate EXPECTED_DATE = LocalDate.of(2026, 10, 5);

    @Autowired
    private BankMatchSuggestHandler suggestHandler;

    @Autowired
    private ChannelSettlementConfirmedHandler settlementHandler;

    @Autowired
    private ReconAdjustmentApprovedHandler adjustmentHandler;

    /** 窗口内（周四，3 个工作日）出建议；窗口外（周五，4 个工作日）、方向不符、金额不等不出；重跑不重复。 */
    @Test
    void test_suggestionRules() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        ExpectedBankMovement movement = movementOn(account, cash, "20.00");
        ExpectedBankMovement other = movementOn(account, cash, "33.00");
        BankStatement statement = BankStatementBuilder.imported(cash, "BS-H1", "0", "0", 4).build();
        BankStatementLine outside = line(statement, 1, SettlementDirection.CREDIT, "33.00", EXPECTED_DATE.plusDays(4));
        BankStatementLine debit = line(statement, 2, SettlementDirection.DEBIT, "20.00", EXPECTED_DATE);
        BankStatementLine unequal = line(statement, 3, SettlementDirection.CREDIT, "19.99", EXPECTED_DATE);
        BankStatementLine inside = line(statement, 4, SettlementDirection.CREDIT, "20.00", EXPECTED_DATE.plusDays(3));
        data.bankStatement(statement, List.of(outside, debit, unequal, inside));

        assertOutcome(CompensationHandlerResult.Outcome.SUCCESS, suggestHandler.handle(event(
            BankMatchSuggestHandler.buildStatementEvent(cash.getId(), statement.getId()))));
        assertOutcome(CompensationHandlerResult.Outcome.SUCCESS, suggestHandler.handle(event(
            BankMatchSuggestHandler.buildMovementEvent(cash.getId(), movement.getId()))));

        List<BankMatch> proposals = bankMatches.listByExpected(movement.getId(), null);
        assertAll(() -> assertEquals(1, proposals.size()),
            () -> assertEquals(inside.getId(), proposals.getFirst().getBankStatementLineId()),
            () -> assertEquals(BankMatchStatus.PROPOSED, proposals.getFirst().getStatus()),
            () -> assertEquals(MatchMethod.AUTO, proposals.getFirst().getMatchMethod()),
            () -> assertEquals(List.of(), bankMatches.listByExpected(other.getId(), null)),
            () -> assertEquals(List.of(), bankMatches.listByLine(debit.getId(), null)),
            () -> assertEquals(List.of(), bankMatches.listByLine(unequal.getId(), null)));
        events.assertNone();
    }

    /** 建议任务身份非法为终态失败；银行账户没有候选时成功且不建议。 */
    @Test
    void test_suggestIdentity() {
        assertAll(
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, suggestHandler.handle(event(
                new CompensationEventRequest("cash:1", BankMatchSuggestHandler.EVENT_TYPE, "BANK_STATEMENT-1")))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, suggestHandler.handle(event(
                new CompensationEventRequest("cash-account:1", BankMatchSuggestHandler.EVENT_TYPE, "OTHER-1")))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.SUCCESS, suggestHandler.handle(event(
                BankMatchSuggestHandler.buildStatementEvent(ReconciliationTestConstants.nextId(), 1L)))));
    }

    /** 发布任务：匹配 / 处置不存在、匹配未确认为终态失败，不发布。 */
    @Test
    void test_publishHandlersFailClosed() {
        CashAccount cash = activeCashAccount();
        ExpectedBankMovement movement = movementOn(clearingAccountFor(cash), cash, "20.00");
        BankStatement statement = BankStatementBuilder.imported(cash, "BS-H2", "0", "20.00", 1).build();
        BankStatementLine line = line(statement, 1, SettlementDirection.CREDIT, "20.00", EXPECTED_DATE);
        data.bankStatement(statement, List.of(line));
        suggestHandler.handle(event(BankMatchSuggestHandler.buildMovementEvent(cash.getId(), movement.getId())));
        BankMatch proposal = bankMatches.listByLine(line.getId(), null).getFirst();

        assertAll(
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, settlementHandler.handle(event(
                ChannelSettlementConfirmedHandler.buildEvent(proposal.getId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, settlementHandler.handle(event(
                ChannelSettlementConfirmedHandler.buildEvent(ReconciliationTestConstants.nextId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, adjustmentHandler.handle(event(
                ReconAdjustmentApprovedHandler.buildEvent(ReconciliationTestConstants.nextId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, adjustmentHandler.handle(event(
                ChannelSettlementConfirmedHandler.buildEvent(proposal.getId())))));
        events.assertNone();
    }

    /** 预期日为 {@link #EXPECTED_DATE} 的渠道预期流水（已对平账单）。 */
    ExpectedBankMovement movementOn(ClearingAccount account, CashAccount cash, String amount) {
        ChannelStatement statement = reconciledStatement(account, SettlementDirection.CREDIT, amount);
        return data.expectedMovement(ExpectedBankMovementBuilder.channelSettlement(statement, cash,
            SettlementDirection.CREDIT, amount).with(item -> item.setExpectedDate(EXPECTED_DATE)).build());
    }

    /** 指定起息日的未匹配流水。 */
    static BankStatementLine line(BankStatement statement, int lineNo, SettlementDirection direction, String amount,
        LocalDate valueDate) {
        BankStatementLine line = BankStatementBuilder.line(statement, lineNo, "H-" + ReconciliationTestConstants.nextId(),
            direction, amount);
        line.setValueDate(valueDate);
        return line;
    }

    /** 以登记请求构造一次执行尝试。 */
    static CompensationEvent event(CompensationEventRequest request) {
        return new CompensationEvent(1L, request.eventKey(), request.eventType(), request.subEventType(), null, 1);
    }

    /** 断言执行结果分类。 */
    static void assertOutcome(CompensationHandlerResult.Outcome expected, CompensationHandlerResult actual) {
        assertEquals(expected, actual.outcome(), actual.safeSummary());
    }
}
