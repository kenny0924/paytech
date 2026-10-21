package com.paytech.reconciliation.auto.ext.compensation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.service.handler.ActualChannelCostConfirmedHandler;
import com.paytech.reconciliation.service.handler.ChannelBatchReconciledHandler;
import com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import java.util.List;

/**
 * 本地补偿 Handler 的身份校验与终态失败（正常执行由调度在 ChannelStatementReconcileTest 等用例中覆盖）
 * <li>事件身份非法、事实不存在为终态失败；账单已不在 MATCHING 时匹配任务直接成功；未对平的账单不发布对平事件。</li>
 *
 * @see ChannelStatementMatchHandler#handle(CompensationEvent)
 * @see ChannelBatchReconciledHandler#handle(CompensationEvent)
 * @see ActualChannelCostConfirmedHandler#handle(CompensationEvent)
 */
class ReconciliationCompensationHandlerTest extends AbstractReconciliationFlowTest {
    @Autowired
    private ChannelStatementMatchHandler matchHandler;

    @Autowired
    private ChannelBatchReconciledHandler batchHandler;

    @Autowired
    private ActualChannelCostConfirmedHandler costHandler;

    /** 匹配任务：身份非法为终态；账单不存在或不在 MATCHING 时成功且不改变状态。 */
    @Test
    void test_matchHandlerIdentity() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement rejected = ChannelStatementBuilder.matching(account, "BATCH-REJ", "1.00", "0", "1.00",
            CashDirection.CREDIT, 0).with(item -> item.setStatus(ChannelStatementStatus.REJECTED)).build();
        data.channelStatement(rejected, List.of());

        assertAll(
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, matchHandler.handle(event(
                new CompensationEventRequest("statement:1", ChannelStatementMatchHandler.EVENT_TYPE,
                    ChannelStatementMatchHandler.TRIGGER_IMPORT)))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, matchHandler.handle(event(
                new CompensationEventRequest("channel-statement:1", ChannelStatementMatchHandler.EVENT_TYPE, "OTHER")))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.SUCCESS, matchHandler.handle(event(
                ChannelStatementMatchHandler.buildEvent(ReconciliationTestConstants.nextId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.SUCCESS, matchHandler.handle(event(
                ChannelStatementMatchHandler.buildResolutionEvent(rejected.getId(), 1L)))));
        assertStatementStatus(rejected.getId(), ChannelStatementStatus.REJECTED);
    }

    /** 发布任务：事件键非法或事实不满足发布条件为终态，不发布。 */
    @Test
    void test_publishHandlersFailClosed() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement matching = ChannelStatementBuilder.matching(account, "BATCH-NOT-REC", "1.00", "0", "1.00",
            CashDirection.CREDIT, 0).build();
        data.channelStatement(matching, List.of());

        assertAll(
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, batchHandler.handle(event(
                ChannelBatchReconciledHandler.buildEvent(matching.getId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, costHandler.handle(event(
                ActualChannelCostConfirmedHandler.buildEvent(ReconciliationTestConstants.nextId())))),
            () -> assertOutcome(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, batchHandler.handle(event(
                ActualChannelCostConfirmedHandler.buildEvent(matching.getId())))));
        events.assertNone();
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
