package com.paytech.ledger.test.base;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodCloseRequestBody;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 期间关账入口测试的公共前置与调用
 * <li>沿用人工调整基类的 AE001/USD 账簿与当月 OPEN 期间，另准备收入（CLIENT_FEE_REVENUE）与费用（CHANNEL_FEE_EXPENSE）账户。</li>
 * <li>请求体以 JSON 字段直接构造：关账与复核的 RequestBody 由并行实现提供，本基类只依赖 HTTP 契约。</li>
 * <li>关账步骤 3（CHECK_ACCOUNT_POSTING_PROGRESS）的外部依赖：每例默认 billing 无已发布事实、account 没有已应用事实也没有过账流，即“已追平”；
 * 需要未追平或不可用的用例自行覆盖账簿主体配置。</li>
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

    /** 每例准备账簿主体的收入与费用账户，并把当月期间设为非年末期间，使结果不随运行月份变化；年末用例自行改为年末。 */
    @BeforeEach
    void setUpPeriodCloseMocks() {
        accountPostings.mockCaughtUpByDefault();
        economicFacts.mockNothingPublishedByDefault();
        makeCurrentPeriodNotYearEnd();
        revenueAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), REVENUE_CODE, LedgerOwnerType.OPERATING_ENTITY,
            ledger.getEntityNo(), BalanceBucket.NONE);
        expenseAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), EXPENSE_CODE, LedgerOwnerType.OPERATING_ENTITY,
            ledger.getEntityNo(), BalanceBucket.NONE);
    }

    /** 让当月期间成为财年最后期间：财年起始月为下月。 */
    protected void makeCurrentPeriodYearEnd() {
        testMapper.updateCalendarYearStartMonth(ledger.getFiscalCalendarId(),
            LocalDate.now().plusMonths(1).getMonthValue());
    }

    /** 让当月期间不是财年最后期间：财年起始月为当月。 */
    protected void makeCurrentPeriodNotYearEnd() {
        testMapper.updateCalendarYearStartMonth(ledger.getFiscalCalendarId(), LocalDate.now().getMonthValue());
    }

    /** 当月期间确认收入：Dr 实收资本 / Cr 收入。 */
    protected void postRevenue(String amount) {
        posted(adjustment().transfer(capitalAccountId, revenueAccountId, amount));
    }

    /** 当月期间确认费用：Dr 费用 / Cr 实收资本。 */
    protected void postExpense(String amount) {
        posted(adjustment().transfer(expenseAccountId, capitalAccountId, amount));
    }

    /** billing 仍有 1 条已批准未发布的事实，使关账步骤 3（入账进度检查）失败。 */
    protected void mockBillingUnpublished() {
        economicFacts.mockProgress(LedgerEntityType.OPERATING_ENTITY.getCode(), ledger.getEntityNo(), ledger.getCurrency(),
            1, 0, null);
    }

    /** 当月期间一笔借记银行现金的调整，使银行账户余额非零。 */
    protected void postBankCash(String amount) {
        posted(adjustment().transfer(bankCashAccountId, capitalAccountId, amount));
    }

    /**
     * 在已关闭的历史期间留下一条借贷不平的分录：先在 OPEN 历史期间以非渠道 / 银行账户过账，再篡改贷方行并关闭期间
     * <li>步骤 2 只检查本期分录，本期关账因而通过步骤 2，但试算的期初余额借贷不平，在步骤 5 失败（第 11b 步起替代账户投影漂移场景）。</li>
     */
    protected void postUnbalancedPriorPeriod() {
        long history = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 31), PeriodStatus.OPEN);
        long entryId = posted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), history)
            .transfer(capitalAccountId, liabilityAccountId, "10")).id("journalEntryId");
        testMapper.updateJournalLineAmount(entryId, 2, "11");
        testMapper.updatePeriodStatus(history, PeriodStatus.CLOSED.getCode());
    }

    /** 关账资源路径。 */
    protected String closesPath(long periodId) {
        return "/accounting-periods/v1/" + periodId + "/closes";
    }

    /** 以模板 Maker 发起关账。 */
    protected LedgerResponseChecker startClose(long periodId) {
        return post(closesPath(periodId), startBody(CLOSE_MAKER_ID));
    }

    /** 对当月期间发起关账。 */
    protected LedgerResponseChecker startClose() {
        return startClose(ledger.getOpenPeriodId());
    }

    /** 批准关账。 */
    protected LedgerResponseChecker approveClose(long periodId, String closeNo, String checkerId, String closeHash) {
        return post(closesPath(periodId) + "/" + closeNo + "/approve",
            reviewBody(checkerId, closeHash));
    }

    /** 驳回关账。 */
    protected LedgerResponseChecker rejectClose(long periodId, String closeNo, String checkerId, String closeHash) {
        return post(closesPath(periodId) + "/" + closeNo + "/reject",
            reviewBody(checkerId, closeHash));
    }

    /** 读取关账详情。 */
    protected LedgerResponseChecker closeDetail(long periodId, String closeNo) {
        return get(closesPath(periodId) + "/" + closeNo);
    }

    /** 关账前提交一笔当月人工调整（尚未审批），用于验证关账中/关账后审批过账被拒。 */
    protected LedgerResponseChecker submitPendingAdjustment() {
        return submitted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(),
            ledger.getOpenPeriodId()).transfer(capitalAccountId, revenueAccountId, "1"));
    }

    /** 审批之前提交的调整。 */
    protected LedgerResponseChecker approvePending(LedgerResponseChecker submit) {
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
