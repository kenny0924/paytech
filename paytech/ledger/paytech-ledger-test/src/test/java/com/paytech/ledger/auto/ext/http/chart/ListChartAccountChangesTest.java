package com.paytech.ledger.auto.ext.http.chart;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.base.AbstractChartAccountApiTest;
import com.paytech.ledger.test.builder.ChartAccountRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 科目变更记录查询：时间倒序、按科目筛选、分页、前后上级编号与拒绝分支。
 *
 * @see LedgerSetupQueryApi#listChartAccountChanges(String, String, Integer, Integer)
 */
class ListChartAccountChangesTest extends AbstractChartAccountApiTest {
    private long bankId;

    /** 通过真实维护入口产生 CREATE、UPDATE、DISABLE 三条记录，另给现金科目一条 DISABLE。 */
    @BeforeEach
    void setUpClassMocks() {
        mockLocalChart();
        create(LOCAL_CHART, ChartAccountRequestBuilder.create("1101", "LOCAL_RECEIVABLES", AccountClass.ASSET,
            NormalSide.DEBIT, false, null)).assertSuccess(201);
        bankId = create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO))
            .assertSuccess(201).id("chartAccountId");
        update(LOCAL_CHART, bankId, ChartAccountRequestBuilder.update("应收银行", "110101", "1101")).assertSuccess(200);
        disable(LOCAL_CHART, bankId).assertSuccess(200);
        disable(LOCAL_CHART, cash.getId()).assertSuccess(200);
    }

    @Test
    void test_listByAccountNewestFirst() {
        List<JsonNode> items = get(changesPath() + "?chartAccountId=" + bankId).assertSuccess(200).assertPage(1, 10, 3);

        assertEquals(List.of(ChartAccountChangeType.DISABLE.getCode(), ChartAccountChangeType.UPDATE.getCode(),
            ChartAccountChangeType.CREATE.getCode()), items.stream().map(item -> item.path("changeType").asString()).toList());
        JsonNode updated = items.get(1);
        assertAll(() -> assertEquals(String.valueOf(bankId), updated.path("chartAccountId").asString()),
            () -> assertEquals("110101", updated.path("accountNo").asString()),
            () -> assertEquals("100102", updated.path("beforeAccountNo").asString()),
            () -> assertEquals("110101", updated.path("afterAccountNo").asString()),
            () -> assertEquals(ASSET_GROUP_NO, updated.path("beforeParentAccountNo").asString()),
            () -> assertEquals("1101", updated.path("afterParentAccountNo").asString()),
            () -> assertEquals("科目 LOCAL_BANK", updated.path("beforeAccountName").asString()),
            () -> assertEquals("应收银行", updated.path("afterAccountName").asString()),
            () -> assertEquals(ChartAccountStatus.ACTIVE.getCode(), updated.path("beforeAccountStatus").asString()),
            () -> assertEquals(OPERATOR_ACCOUNT_ID, updated.path("operatorId").asString()),
            () -> assertTrue(!updated.path("changeTime").asString().isBlank()));
        JsonNode created = items.get(2);
        assertAll(() -> assertTrue(created.path("beforeAccountNo").isNull()),
            () -> assertTrue(created.path("beforeAccountStatus").isNull()),
            () -> assertEquals(ChartAccountStatus.ACTIVE.getCode(), created.path("afterAccountStatus").asString()));
        assertEquals(ChartAccountStatus.INACTIVE.getCode(), items.getFirst().path("afterAccountStatus").asString());
    }

    @Test
    void test_listWholeChartPaged() {
        List<JsonNode> first = get(changesPath() + "?size=10").assertSuccess(200).assertPage(1, 10, 5);
        assertEquals(String.valueOf(cash.getId()), first.getFirst().path("chartAccountId").asString(), "最新的在前");
        get(changesPath() + "?page=2&size=10").assertSuccess(200).assertPage(2, 10, 5);
        get(changesPath() + "?size=7").assertInvalidRequest();
    }

    @Test
    void test_unknownChartOrAccountRejected() {
        get("/chart-of-accounts/v1/NOT_EXISTS/changes").assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
        get(changesPath() + "?chartAccountId=123456789").assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
        get(changesPath() + "?chartAccountId=abc").assertStatus(400);
    }

    /** 测试科目表的变更记录路径。 */
    String changesPath() {
        return "/chart-of-accounts/v1/" + LOCAL_CHART + "/changes";
    }
}
