package com.paytech.ledger.auto.ext.http.chart;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateChartAccountRequestBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.base.AbstractChartAccountApiTest;
import com.paytech.ledger.test.builder.ChartAccountRequestBuilder;

/**
 * 新增科目：末级与汇总、四级编号、采用后追加、编号规则、上级校验、唯一性、科目表状态与操作人 Header。
 *
 * @see ChartOfAccountsCommandApi#createChartAccount(String, String, String, CreateChartAccountRequestBody)
 */
class CreateChartAccountTest extends AbstractChartAccountApiTest {

    @BeforeEach
    void setUpClassMocks() {
        mockLocalChart();
    }

    /** 末级科目挂到汇总科目下：ACTIVE、上级与编号落库，并留下 CREATE 变更记录。 */
    @Test
    void test_normalFlowCreatesLeafWithChangeRecord() {
        long id = create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO))
            .assertSuccess(201).id("chartAccountId");

        ChartAccount account = reload(id);
        assertAll(() -> assertEquals(localChartId, account.getChartId()),
            () -> assertEquals(assetGroup.getId(), account.getParentAccountId()),
            () -> assertEquals("100102", account.getAccountNo()),
            () -> assertEquals("LOCAL_BANK", account.getAccountCode()),
            () -> assertEquals(AccountClass.ASSET, account.getAccountClass()),
            () -> assertEquals(NormalSide.DEBIT, account.getNormalSide()),
            () -> assertEquals(Boolean.TRUE, account.getPostingAllowed()),
            () -> assertEquals(ChartAccountStatus.ACTIVE, account.getAccountStatus()));
        List<ChartAccountChange> changes = testMapper.selectChartAccountChanges(id);
        assertEquals(1, changes.size());
        ChartAccountChange change = changes.getFirst();
        assertAll(() -> assertEquals(ChartAccountChangeType.CREATE, change.getChangeType()),
            () -> assertEquals(OPERATOR_ACCOUNT_ID, change.getOperatorId()),
            () -> assertEquals(localChartId, change.getChartId()),
            () -> assertNull(change.getBeforeAccountNo()), () -> assertNull(change.getBeforeAccountName()),
            () -> assertNull(change.getBeforeParentAccountId()), () -> assertNull(change.getBeforeAccountStatus()),
            () -> assertEquals("100102", change.getAfterAccountNo()),
            () -> assertEquals(assetGroup.getId(), change.getAfterParentAccountId()),
            () -> assertEquals("科目 LOCAL_BANK", change.getAfterAccountName()),
            () -> assertEquals(ChartAccountStatus.ACTIVE, change.getAfterAccountStatus()));
    }

    /** 一级汇总科目下逐级新增到第 4 级；返回的级次由编号长度推出。 */
    @Test
    void test_createFourLevelTree() {
        create(LOCAL_CHART, summary("1101", "LOCAL_RECEIVABLES", null)).assertSuccess(201);
        create(LOCAL_CHART, summary("110101", "LOCAL_TRADE", "1101")).assertSuccess(201);
        create(LOCAL_CHART, summary("11010101", "LOCAL_TRADE_USD", "110101")).assertSuccess(201);
        long leaf = create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("1101010101", "LOCAL_TRADE_USD_A", "11010101"))
            .assertSuccess(201).id("chartAccountId");

        var accounts = get(accountsPath(LOCAL_CHART)).assertSuccess(200).json().path("items");
        var leafBody = StreamSupport.stream(accounts.spliterator(), false)
            .filter(item -> String.valueOf(leaf).equals(item.path("chartAccountId").asString())).findFirst().orElseThrow();
        assertAll(() -> assertEquals(4, leafBody.path("level").asInt()),
            () -> assertEquals("1101010101", leafBody.path("accountNo").asString()),
            () -> assertEquals(ChartAccountStatus.ACTIVE.getCode(), leafBody.path("accountStatus").asString()));
    }

    /** 科目表已被账簿采用后仍可追加末级与一级科目（只追加，不改既有结构）。 */
    @Test
    void test_appendAllowedAfterAdoption() {
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", localChartId);

        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO)).assertSuccess(201);
        create(LOCAL_CHART, summary("1201", "LOCAL_PREPAID", null)).assertSuccess(201);
    }

    /** 编号规则：类别首位、一级 4 位、下级长度为上级加 2 且以上级编号开头。 */
    @Test
    void test_invalidAccountNoRejected() {
        create(LOCAL_CHART, summary("2101", "LOCAL_WRONG_CLASS", null))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), "2101");
        create(LOCAL_CHART, summary("110101", "LOCAL_NO_PARENT", null))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), "110101");
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("10010101", "LOCAL_SKIP_LEVEL", ASSET_GROUP_NO))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), "10010101");
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("110201", "LOCAL_WRONG_PREFIX", ASSET_GROUP_NO))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), "110201");
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("10010", "LOCAL_ODD", ASSET_GROUP_NO))
            .assertProblem(422, "VALIDATION_ERROR").assertFieldError("accountNo");
        assertEquals(0, testMapper.countChartAccountChanges(localChartId), "拒绝时不写变更记录");
    }

    /** 上级不存在、是末级科目、已停用或类别不同时拒绝。 */
    @Test
    void test_invalidParentRejected() {
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("199901", "LOCAL_ORPHAN", "1999"))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), "1999");
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("10010101", "LOCAL_UNDER_LEAF", CASH_NO))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), CASH_NO);
        create(LOCAL_CHART, ChartAccountRequestBuilder.create("100102", "LOCAL_LIABILITY", AccountClass.LIABILITY,
            NormalSide.CREDIT, true, ASSET_GROUP_NO))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), ASSET_GROUP_NO);
        testMapper.updateChartAccountStatus(liabilityGroup.getId(), ChartAccountStatus.INACTIVE.getCode());
        create(LOCAL_CHART, ChartAccountRequestBuilder.create("220101", "LOCAL_PAYABLE", AccountClass.LIABILITY,
            NormalSide.CREDIT, true, LIABILITY_GROUP_NO))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), LIABILITY_GROUP_NO);
    }

    /** 编号或代码在科目表内唯一；同一编号可出现在不同科目表。 */
    @Test
    void test_duplicateNoOrCodeRejected() {
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf(CASH_NO, "LOCAL_OTHER", ASSET_GROUP_NO))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NO_DUPLICATE);
        create(LOCAL_CHART, ChartAccountRequestBuilder.leaf("100102", cash.getAccountCode(), ASSET_GROUP_NO))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_CODE_DUPLICATE);
        assertEquals(0, testMapper.countChartAccountChanges(localChartId), "唯一键冲突整体回滚");

        long otherChart = ledgerMocker.mockChart("OTHER", ReferenceDataStatus.ACTIVE);
        ledgerMocker.mockChartAccount(otherChart, ASSET_GROUP_NO, "OTHER_ASSETS", false);
        create("OTHER", ChartAccountRequestBuilder.leaf(CASH_NO, cash.getAccountCode(), ASSET_GROUP_NO)).assertSuccess(201);
    }

    /** 科目表不存在或未启用。 */
    @Test
    void test_chartNotActiveRejected() {
        ledgerMocker.mockChart("DRAFT_CHART", ReferenceDataStatus.DRAFT);

        create("DRAFT_CHART", summary("1001", "DRAFT_ASSETS", null)).assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
        create("NOT_EXISTS", summary("1001", "MISSING_ASSETS", null))
            .assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
    }

    /** 缺少操作人 Header 或字段非法时参数错误，不写入。 */
    @Test
    void test_invalidParameterRejected() {
        post(accountsPath(LOCAL_CHART), ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO))
            .assertInvalidRequest();
        CreateChartAccountRequestBody lowerCode = ChartAccountRequestBuilder.leaf("100102", "local_bank", ASSET_GROUP_NO);
        create(LOCAL_CHART, lowerCode).assertProblem(422, "VALIDATION_ERROR").assertFieldError("accountCode");
        CreateChartAccountRequestBody noClass = ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO);
        noClass.setAccountClass(null);
        create(LOCAL_CHART, noClass).assertProblem(422, "VALIDATION_ERROR").assertFieldError("accountClass");
        CreateChartAccountRequestBody noPosting = ChartAccountRequestBuilder.leaf("100102", "LOCAL_BANK", ASSET_GROUP_NO);
        noPosting.setPostingAllowed(null);
        create(LOCAL_CHART, noPosting).assertProblem(422, "VALIDATION_ERROR").assertFieldError("postingAllowed");
        assertEquals(0, testMapper.countChartAccountChanges(localChartId));
    }

    /** 资产类借方汇总科目请求。 */
    CreateChartAccountRequestBody summary(String accountNo, String accountCode, String parentAccountNo) {
        return ChartAccountRequestBuilder.create(accountNo, accountCode, AccountClass.ASSET, NormalSide.DEBIT, false,
            parentAccountNo);
    }
}
