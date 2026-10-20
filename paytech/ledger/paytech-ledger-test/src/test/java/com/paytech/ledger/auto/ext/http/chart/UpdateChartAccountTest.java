package com.paytech.ledger.auto.ext.http.chart;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.base.AbstractChartAccountApiTest;
import com.paytech.ledger.test.builder.ChartAccountRequestBuilder;

/**
 * 修改科目：改名、调整上级与编号、无变化不留痕、采用后冻结、有下级、成环与编号规则。
 *
 * @see ChartOfAccountsCommandApi#updateChartAccount(String, String, String, String, UpdateChartAccountRequestBody)
 */
class UpdateChartAccountTest extends AbstractChartAccountApiTest {
    /** 第二个资产汇总科目，作为移动目标。 */
    private ChartAccount otherAssetGroup;

    @BeforeEach
    void setUpClassMocks() {
        mockLocalChart();
        otherAssetGroup = ledgerMocker.mockChartAccount(localChartId, null, "1101", "LOCAL_RECEIVABLES",
            AccountClass.ASSET, NormalSide.DEBIT, false);
    }

    /** 未采用时把末级科目移到另一汇总科目下并重编号：返回与数据库一致，变更记录保存前后值。 */
    @Test
    void test_moveAndRenumberBeforeAdoption() {
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update("应收现金", "110101", "1101"))
            .assertSuccess(200).assertText("accountNo", "110101")
            .assertText("parentChartAccountId", String.valueOf(otherAssetGroup.getId()))
            .assertText("accountName", "应收现金").assertText("level", "2");

        ChartAccount account = reload(cash.getId());
        assertAll(() -> assertEquals("110101", account.getAccountNo()),
            () -> assertEquals(otherAssetGroup.getId(), account.getParentAccountId()),
            () -> assertEquals("应收现金", account.getAccountName()));
        List<ChartAccountChange> changes = testMapper.selectChartAccountChanges(cash.getId());
        assertEquals(1, changes.size());
        ChartAccountChange change = changes.getFirst();
        assertAll(() -> assertEquals(ChartAccountChangeType.UPDATE, change.getChangeType()),
            () -> assertEquals(CASH_NO, change.getBeforeAccountNo()), () -> assertEquals("110101", change.getAfterAccountNo()),
            () -> assertEquals(assetGroup.getId(), change.getBeforeParentAccountId()),
            () -> assertEquals(otherAssetGroup.getId(), change.getAfterParentAccountId()),
            () -> assertEquals(cash.getAccountName(), change.getBeforeAccountName()),
            () -> assertEquals("应收现金", change.getAfterAccountName()),
            () -> assertEquals(ChartAccountStatus.ACTIVE, change.getBeforeAccountStatus()),
            () -> assertEquals(ChartAccountStatus.ACTIVE, change.getAfterAccountStatus()),
            () -> assertEquals(OPERATOR_ACCOUNT_ID, change.getOperatorId()));
    }

    /** 移为一级科目时编号必须是 4 位。 */
    @Test
    void test_moveToTopLevelRequiresFourDigits() {
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), CASH_NO, null))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_NO_INVALID.getCode(), CASH_NO);
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "1201", null))
            .assertSuccess(200).assertText("level", "1");
        assertNull(reload(cash.getId()).getParentAccountId());
    }

    /** 与当前值完全相同：直接返回，不写变更记录。 */
    @Test
    void test_noChangeReturnsWithoutRecord() {
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), CASH_NO, ASSET_GROUP_NO))
            .assertSuccess(200).assertText("accountNo", CASH_NO);

        assertEquals(0, testMapper.countChartAccountChanges(localChartId));
    }

    /** 采用后：改名允许；改编号或上级 CHART_STRUCTURE_FROZEN 且不落库。 */
    @Test
    void test_structureFrozenAfterAdoption() {
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", localChartId);

        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "100102", ASSET_GROUP_NO))
            .assertRejected(LedgerSetupErrorCode.CHART_STRUCTURE_FROZEN);
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "110101", "1101"))
            .assertRejected(LedgerSetupErrorCode.CHART_STRUCTURE_FROZEN);
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update("库存现金", CASH_NO, ASSET_GROUP_NO))
            .assertSuccess(200).assertText("accountName", "库存现金");

        ChartAccount account = reload(cash.getId());
        assertAll(() -> assertEquals(CASH_NO, account.getAccountNo()),
            () -> assertEquals(assetGroup.getId(), account.getParentAccountId()),
            () -> assertEquals(1, testMapper.countChartAccountChanges(localChartId)));
    }

    /** 有下级的科目不能改编号或上级；上级不能是自身。 */
    @Test
    void test_parentWithChildrenOrCycleRejected() {
        update(LOCAL_CHART, assetGroup.getId(), ChartAccountRequestBuilder.update(assetGroup.getAccountName(), "1002", null))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), ASSET_GROUP_NO);
        update(LOCAL_CHART, otherAssetGroup.getId(),
            ChartAccountRequestBuilder.update(otherAssetGroup.getAccountName(), "110101", "1101"))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), "1101");
        update(LOCAL_CHART, otherAssetGroup.getId(),
            ChartAccountRequestBuilder.update(otherAssetGroup.getAccountName(), "100101", ASSET_GROUP_NO))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NO_DUPLICATE);
        assertEquals(0, testMapper.countChartAccountChanges(localChartId));
    }

    /** 新上级必须是同类别、启用的汇总科目。 */
    @Test
    void test_invalidNewParentRejected() {
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "220101",
            LIABILITY_GROUP_NO)).assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(),
            LIABILITY_GROUP_NO);
        testMapper.updateChartAccountStatus(otherAssetGroup.getId(), ChartAccountStatus.INACTIVE.getCode());
        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "110101", "1101"))
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), "1101");
    }

    /** 科目不属于路径科目表或不存在。 */
    @Test
    void test_accountNotInChartRejected() {
        long standardCash = testMapper.selectStandardChartAccountId(StandardAccountCodes.BANK_CASH);
        update(LOCAL_CHART, standardCash, ChartAccountRequestBuilder.update("银行现金", "100101", "1001"))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
        update(LOCAL_CHART, 123456789L, ChartAccountRequestBuilder.update("不存在", "100101", "1001"))
            .assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
    }
}
