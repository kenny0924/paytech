package com.paytech.ledger.auto.ext.http.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.test.base.AbstractPeriodCloseApiTest;

import tools.jackson.databind.JsonNode;

/**
 * 关账查询：期间关账历史按编号降序分页，详情返回步骤、账簿证据与审批记录。
 *
 * @see com.paytech.ledger.api.PeriodCloseApi#listPeriodCloses(String, Integer, Integer)
 * @see com.paytech.ledger.api.PeriodCloseApi#getPeriodClose(String, String)
 */
class PeriodCloseQueryTest extends AbstractPeriodCloseApiTest {

    @Test
    void test_historyListedDescending() {
        postBankCash("10");
        startClose().assertSuccess(201).assertText("status", "FAILED");
        startClose().assertSuccess(201).assertText("status", "FAILED");

        List<JsonNode> items = get(closesPath(ledger.getOpenPeriodId())).assertSuccess(200).assertPage(1, 10, 2);

        assertEquals(List.of("2", "1"), items.stream().map(item -> item.path("closeNo").asString()).toList());
        assertEquals("RECONCILE_ACCOUNT_BALANCES", items.getFirst().path("failedStepType").asString());
    }

    @Test
    void test_detailOfFailedCloseKeepsLockedBookAndNoApproval() {
        postBankCash("10");
        startClose().assertSuccess(201).assertText("status", "FAILED");

        JsonNode detail = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json();

        assertAll(() -> assertEquals(7, detail.path("steps").size()),
            () -> assertEquals("1", detail.path("steps").get(0).path("stepNo").asString()),
            () -> assertEquals("LOCK_SEQUENCE", detail.path("steps").get(0).path("stepType").asString()),
            () -> assertEquals("SUCCEEDED", detail.path("steps").get(0).path("status").asString()),
            () -> assertEquals(1, detail.path("books").size()),
            () -> assertEquals("LOCKED", detail.path("books").get(0).path("status").asString()),
            () -> assertEquals(String.valueOf(ledger.getLedgerBookId()),
                detail.path("books").get(0).path("ledgerBookId").asString()),
            () -> assertEquals(0, detail.path("approvals").size()));
    }

    @Test
    void test_detailOfPendingCloseHasMakerApproval() {
        postRevenue("100");
        var started = startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");

        JsonNode approvals = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json().path("approvals");

        assertEquals(1, approvals.size());
        assertAll(() -> assertEquals(ApprovalRole.MAKER.getCode(), approvals.get(0).path("approvalRole").asString()),
            () -> assertEquals(CLOSE_MAKER_ID, approvals.get(0).path("actorId").asString()),
            () -> assertEquals(started.text("closeHash"), approvals.get(0).path("approvedCloseHash").asString()));
    }

    @Test
    void test_unknownCloseRejected() {
        closeDetail(ledger.getOpenPeriodId(), "9")
            .assertBusinessRejected("关账流程不存在: 期间 " + ledger.getOpenPeriodId() + ", 序号 9");
    }
}
