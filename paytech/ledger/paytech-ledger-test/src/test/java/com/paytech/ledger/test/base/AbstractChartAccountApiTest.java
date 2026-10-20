package com.paytech.ledger.test.base;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.dal.AccountingTestMapper;

/**
 * 科目维护接口测试的公共路径与测试科目表
 * <li>维护只针对测试科目表 LOCAL（主键 ≥ 1,000,000，由全局清理删除），不修改 STANDARD 保留科目的编号、上级与名称。</li>
 * <li>{@link #mockLocalChart()} 由各测试类的 BeforeEach 调用：资产汇总 1001 下挂末级 100101，另有负债汇总 2201。</li>
 */
public abstract class AbstractChartAccountApiTest extends MockTestBase {
    /** 测试科目表代码。 */
    protected static final String LOCAL_CHART = "LOCAL";

    /** 测试资产汇总科目编号。 */
    protected static final String ASSET_GROUP_NO = "1001";

    /** 测试末级现金科目编号。 */
    protected static final String CASH_NO = "100101";

    /** 测试负债汇总科目编号。 */
    protected static final String LIABILITY_GROUP_NO = "2201";

    @Autowired
    protected AccountingTestMapper testMapper;

    protected long localChartId;

    protected ChartAccount assetGroup;

    protected ChartAccount cash;

    protected ChartAccount liabilityGroup;

    /** 插入 ACTIVE 测试科目表及三个科目。 */
    protected void mockLocalChart() {
        localChartId = ledgerMocker.mockChart(LOCAL_CHART, ReferenceDataStatus.ACTIVE);
        assetGroup = ledgerMocker.mockChartAccount(localChartId, null, ASSET_GROUP_NO, "LOCAL_ASSETS", AccountClass.ASSET,
            NormalSide.DEBIT, false);
        cash = ledgerMocker.mockChartAccount(localChartId, assetGroup.getId(), CASH_NO, "LOCAL_CASH", AccountClass.ASSET,
            NormalSide.DEBIT, true);
        liabilityGroup = ledgerMocker.mockChartAccount(localChartId, null, LIABILITY_GROUP_NO, "LOCAL_LIABILITIES",
            AccountClass.LIABILITY, NormalSide.CREDIT, false);
    }

    /** 新增科目。 */
    protected LedgerResponseChecker create(String chartCode, Object body) {
        return postAsOperator(accountsPath(chartCode), body);
    }

    /** 修改科目。 */
    protected LedgerResponseChecker update(String chartCode, long chartAccountId, Object body) {
        return postAsOperator(accountsPath(chartCode) + "/" + chartAccountId + "/update", body);
    }

    /** 停用科目。 */
    protected LedgerResponseChecker disable(String chartCode, long chartAccountId) {
        return postAsOperator(accountsPath(chartCode) + "/" + chartAccountId + "/disable", null);
    }

    /** 启用科目。 */
    protected LedgerResponseChecker enable(String chartCode, long chartAccountId) {
        return postAsOperator(accountsPath(chartCode) + "/" + chartAccountId + "/enable", null);
    }

    /** 科目表下的科目路径。 */
    protected String accountsPath(String chartCode) {
        return "/chart-of-accounts/v1/" + chartCode + "/accounts";
    }

    /** 读取科目当前数据库状态。 */
    protected ChartAccount reload(long chartAccountId) {
        return testMapper.selectChartAccount(chartAccountId);
    }
}
