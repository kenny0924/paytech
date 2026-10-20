package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.app.ext.job.AccountingRuleVersionActivationJobHandler;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.mapper.AccountingRuleLineMapper;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.domain.AccountPostingBatch;
import com.paytech.ledger.domain.AccountPostingCommand;
import com.paytech.ledger.service.processor.response.ActivateDueRuleVersionsResponse;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

/**
 * 自动记账按业务时间选择 STANDARD_TRANSACTION 规则版本（需求 ledger-记账规则按业务时间选版本，AC-01—AC-04、AC-06）
 * <li>第 1 版为初始化 DML 的 5101（2000-01-01 起）；第 2 版由 Mapper 插入，规则行复制 5101，只把 Client 手续费行改记到平台服务费收入，
 * 断言指令冻结的版本、指令行引用的规则行与 Journal 贷方科目，即可区分两版。</li>
 * <li>切换时点 T2 为十分钟前；已退役版本、到期未切换的 APPROVED 版本按生效区间参与，作废 / 驳回 / 草稿 / 待审批 / 零长度 / 已被取代的版本不参与。</li>
 * <li>拒绝场景（业务时间早于首个版本、退役版本缺失效时间）见 {@link TransactionPostingRejectionTest}。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 * @see com.paytech.ledger.domainservice.service.TransactionPostingService#post(com.paytech.ledger.domain.AccountPostingCommand)
 * @see AccountingRuleVersionActivationJobHandler#runOnce()
 */
class RuleVersionSelectionPostingTest extends AbstractTransactionPostingTest {
    /** 初始化 DML 预置的 STANDARD_TRANSACTION Bundle 主键。 */
    private static final long BUNDLE_ID = 5001L;

    /** 初始化 DML 规则行 5201 的行代码：渠道待清算应收（借）。 */
    private static final String GROSS_LINE_CODE = "GROSS_RECEIVABLE";

    /** 初始化 DML 规则行 5203 的行代码：Client 手续费收入（贷）。 */
    private static final String FEE_LINE_CODE = "CLIENT_FEE_REVENUE";

    /** 第 2 版把 Client 手续费行改记到平台服务费收入。 */
    private static final String SECOND_FEE_ACCOUNT = RuleVersionRequestBuilder.PLATFORM_SERVICE_REVENUE;

    /** 规则版本复核人。 */
    private static final long RULE_CHECKER_ID = 81002L;

    @Autowired
    private AccountingRuleVersionActivationJobHandler activationJob;

    @Autowired
    private AccountingRuleLineMapper ruleLineMapper;

    /** 第 2 版生效时间 T2（十分钟前，微秒精度）。 */
    private Instant switchTime;

    /** 本类公共准备：确定切换时点。 */
    @BeforeEach
    void setUpSwitchTime() {
        switchTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(10, ChronoUnit.MINUTES);
    }

    /** AC-01：v1 已退役 [2000-01-01, T2)、v2 自 T2 生效；业务时间早于 T2 的迟到事实按 v1 记账，不被拒绝，也不建 v2 的科目账户。 */
    @Test
    void test_lateFactUsesRetiredVersion() {
        switchToSecondVersion(mockSecondVersion(RuleVersionStatus.ACTIVE));
        BillingFactPayload economics = paymentAt(switchTime.minus(1, ChronoUnit.MINUTES));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();

        postBatch(economics, posting);

        assertPostedWith(economics, posting, INITIAL_VERSION_ID, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE);
        assertNull(account(oeLedger.getLedgerBookId(), SECOND_FEE_ACCOUNT, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO),
            "v1 记账不应使用 v2 的科目");
    }

    /** AC-02：同一切换后，业务时间恰为 T2（区间左闭）的事实按 v2 记账。 */
    @Test
    void test_factAtSwitchTimeUsesNewVersion() {
        long secondId = mockSecondVersion(RuleVersionStatus.ACTIVE);
        switchToSecondVersion(secondId);
        BillingFactPayload economics = paymentAt(switchTime);
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();

        postBatch(economics, posting);

        assertPostedWith(economics, posting, secondId, SECOND_FEE_ACCOUNT);
        assertNull(account(oeLedger.getLedgerBookId(), RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "v2 记账不应使用 v1 的科目");
    }

    /** AC-03：v2 已批准且 T2 已到、定时任务尚未切换（v1 仍为 ACTIVE 当前版本）；业务时间 ≥ T2 按 v2，< T2 按 v1。 */
    @Test
    void test_dueApprovedVersionAppliesBeforeSwitch() {
        long secondId = mockSecondVersion(RuleVersionStatus.APPROVED);
        BillingFactPayload after = paymentAt(switchTime.plus(1, ChronoUnit.MINUTES));
        AccountPostedPayload afterPosting = paymentPosting(after, 1, "97", null).build();
        BillingFactPayload before = paymentAt(switchTime.minus(1, ChronoUnit.MINUTES));
        AccountPostedPayload beforePosting = paymentPosting(before, 2, "97", null).build();

        postBatch(after, afterPosting);
        postBatch(before, beforePosting);

        assertPostedWith(after, afterPosting, secondId, SECOND_FEE_ACCOUNT);
        assertPostedWith(before, beforePosting, INITIAL_VERSION_ID, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE);
        assertAll(() -> assertEquals(RuleVersionStatus.APPROVED, testMapper.selectRuleVersion(secondId).getStatus()),
            () -> assertEquals(RuleVersionStatus.ACTIVE, testMapper.selectRuleVersion(INITIAL_VERSION_ID).getStatus(),
                "记账只读版本，不代替定时任务切换"));
    }

    /**
     * AC-04：v1 为 ACTIVE 当前版本；同时存在生效时间已到的零长度 RETIRED、VOIDED、REJECTED、DRAFT、PENDING_APPROVAL 版本，
     * 以及生效时间不晚于 v1、等待置为 RETIRED 的已被取代 APPROVED 版本；它们都改记手续费科目，却都不被选中，事实按 v1 记账。
     */
    @Test
    void test_nonEffectiveVersionsNeverSelected() {
        mockVersion(2, RuleVersionStatus.RETIRED, switchTime, switchTime);
        mockVersion(3, RuleVersionStatus.VOIDED, switchTime, null);
        mockVersion(4, RuleVersionStatus.REJECTED, switchTime, null);
        mockVersion(5, RuleVersionStatus.DRAFT, switchTime, null);
        mockVersion(6, RuleVersionStatus.PENDING_APPROVAL, switchTime, null);
        mockVersion(7, RuleVersionStatus.APPROVED, Instant.parse("1999-06-01T00:00:00Z"), null);
        BillingFactPayload economics = paymentAt(switchTime);
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();

        postBatch(economics, posting);

        assertPostedWith(economics, posting, INITIAL_VERSION_ID, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE);
        assertNull(account(oeLedger.getLedgerBookId(), SECOND_FEE_ACCOUNT, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO),
            "未生效版本的科目不应被使用");
    }

    /**
     * AC-06：同一事实在定时任务切换前处理（回滚，仅观察选中的版本）与切换后真实过账，选中的版本相同
     * <li>切换前 v2 为到期未切换的 APPROVED；生产定时任务切换后 v1 RETIRED 且失效时间等于 T2、v2 ACTIVE。</li>
     */
    @Test
    void test_sameFactSelectsSameVersionAcrossSwitch() {
        long secondId = mockSecondVersion(RuleVersionStatus.APPROVED);
        BillingFactPayload economics = paymentAt(switchTime.plus(1, ChronoUnit.MINUTES));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();
        long beforeSwitch = selectedVersionRolledBack(economics, posting);

        ActivateDueRuleVersionsResponse activation = activationJob.runOnce();
        postBatch(economics, posting);

        assertAll(() -> assertEquals(secondId, beforeSwitch, "切换前选中 v2"),
            () -> assertEquals(1, activation.getActivatedCount()),
            () -> assertEquals(switchTime, testMapper.selectRuleVersion(INITIAL_VERSION_ID).getEffectiveTo()));
        assertPostedWith(economics, posting, secondId, SECOND_FEE_ACCOUNT);
    }

    /** 插入第 2 版（自 T2 起、无失效时间），返回版本主键。 */
    long mockSecondVersion(RuleVersionStatus status) {
        return mockVersion(2, status, switchTime, null);
    }

    /** 插入改记手续费科目的规则版本，返回版本主键；复核过的状态带复核人与批准时间。 */
    long mockVersion(long bundleVer, RuleVersionStatus status, Instant effectiveFrom, Instant effectiveTo) {
        AccountingRuleBundleVer ver = new AccountingRuleBundleVer();
        ver.setBundleId(BUNDLE_ID);
        ver.setBundleVer(bundleVer);
        ver.setStatus(status);
        ver.setEffectiveFrom(effectiveFrom);
        ver.setEffectiveTo(effectiveTo);
        if (status == RuleVersionStatus.APPROVED || status == RuleVersionStatus.ACTIVE
            || status == RuleVersionStatus.RETIRED) {
            ver.setCheckerId(RULE_CHECKER_ID);
            ver.setApprovedTime(effectiveFrom);
        }
        return ledgerMocker.mockRuleVersion(ver, INITIAL_VERSION_ID, Map.of(FEE_LINE_CODE, SECOND_FEE_ACCOUNT));
    }

    /** 构造已切换到第 2 版的状态：v1 RETIRED 且失效时间为 T2，Bundle 当前指针指向 v2（清理组件恢复初始化行）。 */
    void switchToSecondVersion(long secondId) {
        testMapper.retireRuleVersion(INITIAL_VERSION_ID, switchTime);
        testMapper.updateRuleBundleCurrentVersion(BUNDLE_ID, secondId);
    }

    /** 业务时间为给定时点的无 Partner 支付：gross 100 = 本金 97 + 手续费 3。 */
    BillingFactPayload paymentAt(Instant businessTime) {
        return fact(payment("100").businessTime(businessTime), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
    }

    /** 在回滚事务内调用过账引擎，返回本次生成的支付指令冻结的规则版本；不留下任何数据。 */
    long selectedVersionRolledBack(BillingFactPayload economics, AccountPostedPayload posting) {
        return transactionTemplate.execute(status -> {
            postingService.post(new AccountPostingCommand(AccountPostingBatch.of(posting), economics));
            long verId = instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED).getAccountingRuleBundleVerId();
            status.setRollbackOnly();
            return verId;
        });
    }

    /** 断言支付指令冻结给定版本、指令行引用该版本的规则行，Journal 把手续费 3 贷记到给定科目的 OE 账户。 */
    void assertPostedWith(BillingFactPayload economics, AccountPostedPayload posting, long verId, String feeAccountCode) {
        long book = oeLedger.getLedgerBookId();
        AccountingInstruction instruction = instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED);
        JournalEntry journal = assertPostingInstruction(instruction, economics, posting, book, verId,
            businessDate(economics));
        long receivable = existingAccount(book, RuleVersionRequestBuilder.CHANNEL_UNSETTLED_RECEIVABLE,
            LedgerOwnerType.CHANNEL_ACCOUNT, String.valueOf(CHANNEL_ACCOUNT_ID));
        long fee = existingAccount(book, feeAccountCode, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO);
        assertInstructionLines(instruction.getId(), debit(ruleLineId(verId, GROSS_LINE_CODE), receivable, "100"),
            credit(ruleLineId(verId, FEE_LINE_CODE), fee, "3"),
            entry(posting.getEntries().get(0), controlAccount(StandardAccountCodes.CLIENT_PAYABLE)));
        assertTrue(ledgerChecker.journalLines(journal.getId()).stream().anyMatch(line -> line.getAccountId() == fee
                && line.getEntrySide() == EntrySide.CREDIT && new BigDecimal("3").compareTo(line.getAmount()) == 0),
            "Journal 应把手续费 3 贷记到 " + feeAccountCode);
    }

    /** 版本内指定行代码的规则行主键；缺失时失败。 */
    long ruleLineId(long verId, String lineCode) {
        List<AccountingRuleLine> lines = ruleLineMapper.selectByVersion(verId).stream()
            .filter(line -> lineCode.equals(line.getLineCode())).toList();
        assertEquals(1, lines.size(), "版本 " + verId + " 行代码 " + lineCode);
        return lines.get(0).getId();
    }
}
