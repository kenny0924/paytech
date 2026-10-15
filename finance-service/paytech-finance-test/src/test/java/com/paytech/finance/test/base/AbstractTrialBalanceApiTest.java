package com.paytech.finance.test.base;

import java.time.LocalDate;

import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.finance.test.checker.FinanceResponseChecker;

/**
 * 试算平衡入口测试的公共路径与数据准备
 * <li>沿用人工调整基类的 AE001/USD 账簿、当前 OPEN 期间与三个账户，通过真实审批过账形成 Journal。</li>
 */
public abstract class AbstractTrialBalanceApiTest extends AbstractManualAdjustmentApiTest {
    /** 历史期间结束日；过账日期收敛到该日。 */
    protected static final LocalDate HISTORY_END = LocalDate.of(2020, 1, 31);

    /** 在本账簿日历下创建 2020-01 的 OPEN 历史期间。 */
    protected long mockHistoryPeriod() {
        return ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1), HISTORY_END,
            PeriodStatus.OPEN);
    }

    /** 在指定期间提交并批准一笔借贷同额调整。 */
    protected void postedIn(long periodId, long debitAccountId, long creditAccountId, String amount) {
        posted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), periodId)
            .transfer(debitAccountId, creditAccountId, amount));
    }

    /** 对本账簿指定期间执行试算。 */
    protected FinanceResponseChecker runTrial(long periodId) {
        return post(trialPath(ledger.getLedgerBookId(), periodId), null);
    }

    /** 试算资源路径。 */
    protected String trialPath(long ledgerBookId, long periodId) {
        return "/ledger-books/v1/" + ledgerBookId + "/periods/" + periodId + "/trial-balances";
    }
}
