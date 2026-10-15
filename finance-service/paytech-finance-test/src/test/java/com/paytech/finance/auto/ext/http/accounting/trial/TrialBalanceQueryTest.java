package com.paytech.finance.auto.ext.http.accounting.trial;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;
import com.paytech.finance.accounting.core.enums.TrialExceptionType;
import com.paytech.finance.test.base.AbstractTrialBalanceApiTest;

import tools.jackson.databind.JsonNode;

/**
 * 试算查询：列表按编号降序分页、详情的行与异常、编号不存在拒绝。
 *
 * @see com.paytech.finance.api.accounting.TrialBalanceApi#listTrialBalances(String, String, Integer, Integer)
 * @see com.paytech.finance.api.accounting.TrialBalanceApi#getTrialBalance(String, String, String)
 */
class TrialBalanceQueryTest extends AbstractTrialBalanceApiTest {

    @Test
    void test_listDescendingWithPaging() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "1");
        for (int i = 0; i < 11; i++) {
            runTrial(ledger.getOpenPeriodId()).assertSuccess(201);
        }

        String path = trialPath(ledger.getLedgerBookId(), ledger.getOpenPeriodId());
        List<JsonNode> first = get(path + "?size=10").assertSuccess(200).assertPage(1, 10, 11);
        List<JsonNode> second = get(path + "?page=2&size=10").assertSuccess(200).assertPage(2, 10, 11);

        assertEquals("11", first.getFirst().path("runNo").asString());
        assertEquals("2", first.getLast().path("runNo").asString());
        assertEquals("1", second.getFirst().path("runNo").asString());
        assertEquals(TrialBalanceStatus.SUCCEEDED.getCode(), second.getFirst().path("status").asString());
    }

    @Test
    void test_detailIncludesLinesAndExceptions() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "20");
        testMapper.updateAccountNetBalance(capitalAccountId, "25");
        String runNo = runTrial(ledger.getOpenPeriodId()).assertSuccess(201).text("runNo");

        JsonNode detail = get(trialPath(ledger.getLedgerBookId(), ledger.getOpenPeriodId()) + "/" + runNo)
            .assertSuccess(200).as(JsonNode.class);

        assertAll(() -> assertEquals(TrialBalanceStatus.FAILED.getCode(), detail.path("status").asString()),
            () -> assertEquals(String.valueOf(ledger.getLedgerBookId()), detail.path("ledgerBookId").asString()),
            () -> assertEquals(String.valueOf(ledger.getOpenPeriodId()), detail.path("periodId").asString()),
            () -> assertEquals(2, detail.path("lines").size()),
            () -> assertEquals("20.00", detail.path("lines").get(0).path("closingBalance").asString()),
            () -> assertEquals(1, detail.path("exceptions").size()),
            () -> assertEquals(TrialExceptionType.GENERAL_LEDGER_MISMATCH.getCode(),
                detail.path("exceptions").get(0).path("exceptionType").asString()),
            () -> assertEquals(String.valueOf(capitalAccountId),
                detail.path("exceptions").get(0).path("referenceId").asString()),
            () -> assertEquals("5.00", detail.path("exceptions").get(0).path("differenceAmount").asString()));
    }

    @Test
    void test_unknownRunNoRejected() {
        get(trialPath(ledger.getLedgerBookId(), ledger.getOpenPeriodId()) + "/9").assertBusinessRejected(
            "试算不存在: 账簿 " + ledger.getLedgerBookId() + ", 期间 " + ledger.getOpenPeriodId() + ", 序号 9");
    }
}
