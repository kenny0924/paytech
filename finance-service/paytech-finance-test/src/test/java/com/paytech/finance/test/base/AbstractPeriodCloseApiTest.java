package com.paytech.finance.test.base;


import org.junit.jupiter.api.BeforeEach;

import com.paytech.finance.api.accounting.request.StartPeriodCloseRequestBody;
import com.paytech.finance.api.accounting.request.ReviewPeriodCloseRequestBody;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.finance.test.checker.FinanceResponseChecker;

/**
 * 期间关账入口测试的公共前置与调用
 * <li>沿用人工调整基类的 AE001/USD 账簿与当月 OPEN 期间，另准备收入（CLIENT_FEE_REVENUE）与费用（CHANNEL_FEE_EXPENSE）账户。</li>
 * <li>正常关账用例以实收资本作为对方科目，避免银行现金余额触发余额核对步骤失败。</li>
 * <li>请求体以 JSON 字段直接构造：关账与复核的 RequestBody 由并行实现提供，本基类只依赖 HTTP 契约。</li>
 */
public abstract class AbstractPeriodCloseApiTest extends AbstractManualAdjustmentApiTest {
    /** 关账 Maker；与调整模板编制人相同不影响关账。 */
    protected static final String CLOSE_MAKER_ID = "80001";

    /** 关账 Checker。 */
    protected static final String CLOSE_CHECKER_ID = "80002";

    /** 收入科目代码。 */
    protected static final String REVENUE_CODE = "CLIENT_FEE_REVENUE";

    /** 费用科目代码。 */
    protected static final String EXPENSE_CODE = "CHANNEL_FEE_EXPENSE";

    /** 本期损益科目代码。 */
    protected static final String CURRENT_RESULT_CODE = "CURRENT_PERIOD_RESULT";

    protected long revenueAccountId;

    protected long expenseAccountId;

    /** 每例准备账簿主体的收入与费用账户。 */
    @BeforeEach
    void setUpPeriodCloseMocks() {
        revenueAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), REVENUE_CODE, OwnerType.OPERATING_ENTITY,
            ledger.getEntityNo(), BalanceBucket.NONE);
        expenseAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), EXPENSE_CODE, OwnerType.OPERATING_ENTITY,
            ledger.getEntityNo(), BalanceBucket.NONE);
    }

    /** 当月期间确认收入：Dr 实收资本 / Cr 收入。 */
    protected void postRevenue(String amount) {
        posted(adjustment().transfer(capitalAccountId, revenueAccountId, amount));
    }

    /** 当月期间确认费用：Dr 费用 / Cr 实收资本。 */
    protected void postExpense(String amount) {
        posted(adjustment().transfer(expenseAccountId, capitalAccountId, amount));
    }

    /** 当月期间一笔借记银行现金的调整，使银行账户余额非零。 */
    protected void postBankCash(String amount) {
        posted(adjustment().transfer(bankCashAccountId, capitalAccountId, amount));
    }

    /** 关账资源路径。 */
    protected String closesPath(long periodId) {
        return "/accounting-periods/v1/" + periodId + "/closes";
    }

    /** 以模板 Maker 发起关账。 */
    protected FinanceResponseChecker startClose(long periodId) {
        return post(closesPath(periodId), startBody(CLOSE_MAKER_ID));
    }

    /** 对当月期间发起关账。 */
    protected FinanceResponseChecker startClose() {
        return startClose(ledger.getOpenPeriodId());
    }

    /** 批准关账。 */
    protected FinanceResponseChecker approveClose(long periodId, String closeNo, String checkerId, String closeHash) {
        return post(closesPath(periodId) + "/" + closeNo + "/approve",
            reviewBody(checkerId, closeHash));
    }

    /** 驳回关账。 */
    protected FinanceResponseChecker rejectClose(long periodId, String closeNo, String checkerId, String closeHash) {
        return post(closesPath(periodId) + "/" + closeNo + "/reject",
            reviewBody(checkerId, closeHash));
    }

    /** 读取关账详情。 */
    protected FinanceResponseChecker closeDetail(long periodId, String closeNo) {
        return get(closesPath(periodId) + "/" + closeNo);
    }

    /** 关账前提交一笔当月人工调整（尚未审批），用于验证关账中/关账后审批过账被拒。 */
    protected FinanceResponseChecker submitPendingAdjustment() {
        return submitted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(),
            ledger.getOpenPeriodId()).transfer(capitalAccountId, revenueAccountId, "1"));
    }

    /** 审批之前提交的调整。 */
    protected FinanceResponseChecker approvePending(FinanceResponseChecker submit) {
        return approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash"));
    }

    /** 发起关账请求体。 */
    protected StartPeriodCloseRequestBody startBody(String makerId) {
        StartPeriodCloseRequestBody body = new StartPeriodCloseRequestBody();
        body.setMakerId(makerId);
        return body;
    }

    /** 关账复核请求体。 */
    protected ReviewPeriodCloseRequestBody reviewBody(String checkerId, String approvedCloseHash) {
        ReviewPeriodCloseRequestBody body = new ReviewPeriodCloseRequestBody();
        body.setCheckerId(checkerId);
        body.setApprovedCloseHash(approvedCloseHash);
        return body;
    }
}
