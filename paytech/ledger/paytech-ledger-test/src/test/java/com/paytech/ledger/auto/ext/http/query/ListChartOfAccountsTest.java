package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 科目表与科目只读列表：科目表按代码排序、科目按编号排序与可过账 / 启停过滤、科目表不存在。
 *
 * @see LedgerSetupQueryApi#listChartsOfAccounts()
 * @see LedgerSetupQueryApi#listChartAccounts(String, Boolean, String)
 */
class ListChartOfAccountsTest extends MockTestBase {
    /** 初始化 DML 中 STANDARD 科目表的科目数量：一级汇总 19 + 二级汇总 26 + 三级末级 29。 */
    private static final int STANDARD_ACCOUNT_COUNT = 74;

    /** STANDARD 汇总科目数量（一级 19 + 二级 26）。 */
    private static final int STANDARD_SUMMARY_COUNT = 45;

    @Autowired
    private AccountingTestMapper testMapper;

    private long localChart;

    @BeforeEach
    void setUpClassMocks() {
        localChart = ledgerMocker.mockChart("LOCAL", ReferenceDataStatus.DRAFT);
        ChartAccount parent = ledgerMocker.mockChartAccount(localChart, null, "1001", "LOCAL_PARENT",
            AccountClass.ASSET, NormalSide.DEBIT, false);
        ledgerMocker.mockChartAccount(localChart, parent.getId(), "100101", "LOCAL_CASH", AccountClass.ASSET,
            NormalSide.DEBIT, true);
    }

    @Test
    void test_listChartsSortedByCode() {
        List<JsonNode> charts = items(get("/chart-of-accounts/v1").assertSuccess(200).json());

        assertEquals(List.of("LOCAL", "STANDARD"), charts.stream().map(item -> item.path("chartCode").asString()).toList());
        JsonNode standard = charts.get(1);
        assertAll(() -> assertEquals(String.valueOf(LedgerMocker.STANDARD_CHART_ID), standard.path("chartId").asString()),
            () -> assertEquals(ReferenceDataStatus.ACTIVE.getCode(), standard.path("status").asString()),
            () -> assertTrue(!standard.path("chartName").asString().isBlank()),
            () -> assertEquals(ReferenceDataStatus.DRAFT.getCode(), charts.get(0).path("status").asString()));
    }

    @Test
    void test_listChartAccountsSortedAndFiltered() {
        List<JsonNode> standard = items(get(accountsPath("STANDARD")).assertSuccess(200).json());
        assertEquals(STANDARD_ACCOUNT_COUNT, standard.size());
        List<String> numbers = standard.stream().map(item -> item.path("accountNo").asString()).toList();
        assertEquals(numbers.stream().sorted().toList(), numbers, "按科目编号升序");
        JsonNode bankCash = standard.stream()
            .filter(item -> StandardAccountCodes.BANK_CASH.equals(item.path("accountCode").asString()))
            .findFirst().orElseThrow();
        assertAll(() -> assertEquals(String.valueOf(LedgerMocker.STANDARD_CHART_ID), bankCash.path("chartId").asString()),
            () -> assertEquals("10010101", bankCash.path("accountNo").asString()),
            () -> assertEquals(3, bankCash.path("level").asInt()),
            () -> assertEquals(ChartAccountStatus.ACTIVE.getCode(), bankCash.path("accountStatus").asString()),
            () -> assertEquals(AccountClass.ASSET.getCode(), bankCash.path("accountClass").asString()),
            () -> assertEquals(NormalSide.DEBIT.getCode(), bankCash.path("normalSide").asString()),
            () -> assertTrue(bankCash.path("postingAllowed").asBoolean()));

        assertEquals(List.of("LOCAL_PARENT", "LOCAL_CASH"), codes(accountsPath("LOCAL")));
        assertEquals(List.of("LOCAL_CASH"), codes(accountsPath("LOCAL") + "?postingAllowed=true"));
        assertEquals(List.of("LOCAL_PARENT"), codes(accountsPath("LOCAL") + "?postingAllowed=false"));
        assertEquals(STANDARD_SUMMARY_COUNT, codes(accountsPath("STANDARD") + "?postingAllowed=false").size());
    }

    /** 启停过滤：停用科目只出现在 INACTIVE 结果中。 */
    @Test
    void test_listChartAccountsFilteredByStatus() {
        testMapper.updateStandardChartAccountStatus(StandardAccountCodes.CHANNEL_FEE_ACCRUAL,
            ChartAccountStatus.INACTIVE.getCode());

        assertEquals(List.of(StandardAccountCodes.CHANNEL_FEE_ACCRUAL),
            codes(accountsPath("STANDARD") + "?accountStatus=" + ChartAccountStatus.INACTIVE.getCode()));
        assertEquals(STANDARD_ACCOUNT_COUNT - 1,
            codes(accountsPath("STANDARD") + "?accountStatus=" + ChartAccountStatus.ACTIVE.getCode()).size());
        get(accountsPath("STANDARD") + "?accountStatus=CLOSED").assertStatus(400);
    }

    @Test
    void test_unknownChartRejected() {
        get(accountsPath("NOT_EXISTS")).assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
        get(accountsPath("lower")).assertStatus(400);
    }

    /** 读取科目代码列表。 */
    List<String> codes(String path) {
        return items(get(path).assertSuccess(200).json()).stream().map(item -> item.path("accountCode").asString())
            .toList();
    }

    /** 列表响应的 items。 */
    List<JsonNode> items(JsonNode body) {
        List<JsonNode> items = new ArrayList<>();
        body.path("items").forEach(items::add);
        return items;
    }

    /** 科目表下的科目路径。 */
    String accountsPath(String chartCode) {
        return "/chart-of-accounts/v1/" + chartCode + "/accounts";
    }
}
