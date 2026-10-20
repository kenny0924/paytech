package com.paytech.ledger.auto.ext.http.chart;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.AbstractChartAccountApiTest;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 停用与启用科目：幂等、变更记录、采用后仍可启停、引用检查（账户类型目录、记账规则、结转规则、启用中的下级）与上级启用要求。
 *
 * @see ChartOfAccountsCommandApi#disableChartAccount(String, String, String, String)
 * @see ChartOfAccountsCommandApi#enableChartAccount(String, String, String, String)
 */
class DisableEnableChartAccountTest extends AbstractChartAccountApiTest {
    /** STANDARD 中只被损益结转规则引用的留存收益科目。 */
    private static final String RETAINED_EARNINGS_CODE = "RETAINED_EARNINGS";

    @BeforeEach
    void setUpClassMocks() {
        mockLocalChart();
    }

    /** 停用、重复停用、启用、重复启用：状态与变更记录只在实际变化时写入。 */
    @Test
    void test_disableAndEnableAreIdempotent() {
        disable(LOCAL_CHART, cash.getId()).assertSuccess(200)
            .assertText("accountStatus", ChartAccountStatus.INACTIVE.getCode());
        disable(LOCAL_CHART, cash.getId()).assertSuccess(200)
            .assertText("accountStatus", ChartAccountStatus.INACTIVE.getCode());
        assertEquals(ChartAccountStatus.INACTIVE, reload(cash.getId()).getAccountStatus());
        enable(LOCAL_CHART, cash.getId()).assertSuccess(200)
            .assertText("accountStatus", ChartAccountStatus.ACTIVE.getCode());
        enable(LOCAL_CHART, cash.getId()).assertSuccess(200);

        List<ChartAccountChange> changes = testMapper.selectChartAccountChanges(cash.getId());
        assertEquals(List.of(ChartAccountChangeType.DISABLE, ChartAccountChangeType.ENABLE),
            changes.stream().map(ChartAccountChange::getChangeType).toList());
        ChartAccountChange disabled = changes.getFirst();
        assertAll(() -> assertEquals(ChartAccountStatus.ACTIVE, disabled.getBeforeAccountStatus()),
            () -> assertEquals(ChartAccountStatus.INACTIVE, disabled.getAfterAccountStatus()),
            () -> assertEquals(CASH_NO, disabled.getBeforeAccountNo()),
            () -> assertEquals(CASH_NO, disabled.getAfterAccountNo()),
            () -> assertEquals(assetGroup.getId(), disabled.getAfterParentAccountId()),
            () -> assertEquals(OPERATOR_ACCOUNT_ID, disabled.getOperatorId()));
    }

    /** 汇总科目有启用中的下级时不能停用；下级停用后可停用；上级停用时下级不能启用。 */
    @Test
    void test_summaryRequiresChildrenDisabledAndParentEnabled() {
        disable(LOCAL_CHART, assetGroup.getId())
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_IN_USE.getCode(), "仍有启用中的下级");
        disable(LOCAL_CHART, cash.getId()).assertSuccess(200);
        disable(LOCAL_CHART, assetGroup.getId()).assertSuccess(200);

        enable(LOCAL_CHART, cash.getId())
            .assertRejectedWithCode(LedgerSetupErrorCode.CHART_PARENT_INVALID.getCode(), CASH_NO);
        enable(LOCAL_CHART, assetGroup.getId()).assertSuccess(200);
        enable(LOCAL_CHART, cash.getId()).assertSuccess(200);
        assertEquals(ChartAccountStatus.ACTIVE, reload(cash.getId()).getAccountStatus());
    }

    /** 科目表被采用后仍允许停用与启用。 */
    @Test
    void test_statusChangeAllowedAfterAdoption() {
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", localChartId);

        disable(LOCAL_CHART, cash.getId()).assertSuccess(200);
        enable(LOCAL_CHART, cash.getId()).assertSuccess(200);
    }

    /** 账户类型目录、当前生效记账规则与损益结转规则引用的科目不能停用，状态保持 ACTIVE。 */
    @Test
    void test_referencedAccountsCannotBeDisabled() {
        assertInUse(StandardAccountCodes.CLIENT_PAYABLE, "属于 account 账户类型目录");
        assertInUse(StandardAccountCodes.BANK_CASH, "属于 account 账户类型目录");
        assertInUse(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, "被当前生效的记账规则引用");
        assertInUse(RETAINED_EARNINGS_CODE, "被损益结转规则引用");
        assertEquals(0, testMapper.countChartAccountChanges(LedgerMocker.STANDARD_CHART_ID));
    }

    /** 科目不属于路径科目表时拒绝。 */
    @Test
    void test_accountNotInChartRejected() {
        disable("STANDARD", cash.getId()).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
        enable("STANDARD", cash.getId()).assertRejected(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
    }

    /** 停用 STANDARD 科目被拒绝且状态不变。 */
    void assertInUse(String accountCode, String reason) {
        long id = testMapper.selectStandardChartAccountId(accountCode);
        disable("STANDARD", id).assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_IN_USE.getCode(), reason);
        assertEquals(ChartAccountStatus.ACTIVE, reload(id).getAccountStatus(), accountCode);
    }
}
