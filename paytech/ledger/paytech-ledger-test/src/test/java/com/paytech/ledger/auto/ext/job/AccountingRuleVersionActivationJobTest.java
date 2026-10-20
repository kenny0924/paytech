package com.paytech.ledger.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.app.ext.job.AccountingRuleVersionActivationJobHandler;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.service.processor.response.ActivateDueRuleVersionsResponse;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

/**
 * 规则版本到期生效：显式触发生产 XXL-Job Handler，经 ActivateDueRuleVersionsProcessor / ActivateRuleVersionProcessor 读写本地 MySQL
 * <li>到期的 APPROVED 版本切换为 ACTIVE、原当前版本 RETIRED（失效时间 = 新版本生效时间）、Bundle 指针切换；未到期不动；重复执行幂等；
 * 生效时间不晚于当前版本的到期版本已被取代，置为 RETIRED 而不生效。</li>
 * <li>前置版本经真实 HTTP 入口创建、提交与批准（未来生效），再由 TestMapper 把生效时间改到过去，构造“已批准且到期”。</li>
 *
 * @see AccountingRuleVersionActivationJobHandler#runOnce()
 * @see AccountingRuleVersionActivationJobHandler#activate()
 */
class AccountingRuleVersionActivationJobTest extends AbstractRuleBundleApiTest {
    @Autowired
    private AccountingRuleVersionActivationJobHandler job;

    /** 到期：第 2 版 ACTIVE，第 1 版 RETIRED 且失效时间等于第 2 版生效时间，Bundle 指针切换且乐观锁版本加一。 */
    @Test
    void test_dueApprovedVersionActivated() {
        long versionId = approvedInFuture();
        Instant effectiveFrom = makeDue(versionId, pastEffectiveFrom());

        ActivateDueRuleVersionsResponse result = job.runOnce();

        assertAll(() -> assertEquals(1, result.getActivatedCount()), () -> assertEquals(0, result.getRetiredCount()),
            () -> assertEquals(0, result.getFailedCount()));
        AccountingRuleBundleVer activated = testMapper.selectRuleVersion(versionId);
        AccountingRuleBundleVer retired = testMapper.selectRuleVersion(INITIAL_VERSION_ID);
        assertAll(() -> assertEquals(RuleVersionStatus.ACTIVE, activated.getStatus()),
            () -> assertNull(activated.getEffectiveTo()), () -> assertEquals(Long.valueOf(CHECKER_ID), activated.getCheckerId()),
            () -> assertEquals(RuleVersionStatus.RETIRED, retired.getStatus()),
            () -> assertEquals(effectiveFrom, retired.getEffectiveTo()));
        assertPointer(versionId, 1L);
        getBundle(BUNDLE_CODE).assertSuccess(200).assertText("currentBundleVer", "2");
    }

    /** 未到期：不切换，版本保持 APPROVED，指针不变。 */
    @Test
    void test_notYetDueUntouched() {
        long versionId = approvedInFuture();

        ActivateDueRuleVersionsResponse result = job.runOnce();

        assertAll(() -> assertEquals(0, result.getActivatedCount()), () -> assertEquals(0, result.getFailedCount()));
        assertEquals(RuleVersionStatus.APPROVED, testMapper.selectRuleVersion(versionId).getStatus());
        assertEquals(RuleVersionStatus.ACTIVE, testMapper.selectRuleVersion(INITIAL_VERSION_ID).getStatus());
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 重复执行幂等：第二轮没有到期版本，状态与 Bundle 乐观锁版本不再变化；XXL 入口方法同样不改变结果。 */
    @Test
    void test_repeatedRunIdempotent() {
        long versionId = approvedInFuture();
        makeDue(versionId, pastEffectiveFrom());
        job.runOnce();

        ActivateDueRuleVersionsResponse second = job.runOnce();
        job.activate();

        assertAll(() -> assertEquals(0, second.getActivatedCount()), () -> assertEquals(0, second.getFailedCount()));
        assertEquals(RuleVersionStatus.ACTIVE, testMapper.selectRuleVersion(versionId).getStatus());
        assertPointer(versionId, 1L);
    }

    /** XXL 入口方法：到期版本经 @XxlJob 方法同样切换为 ACTIVE。 */
    @Test
    void test_xxlEntryActivates() {
        long versionId = approvedInFuture();
        makeDue(versionId, pastEffectiveFrom());

        job.activate();

        assertEquals(RuleVersionStatus.ACTIVE, testMapper.selectRuleVersion(versionId).getStatus());
        assertPointer(versionId, 1L);
    }

    /**
     * 已被取代：第 2 版批准为明天生效后，第 3 版以一分钟前立即生效；任务停机期间第 2 版的生效时间早于第 3 版
     * <li>本轮不生效第 2 版，而是置为 RETIRED（失效时间等于自身生效时间），第 3 版与指针不变，不计失败；下一轮没有到期版本。</li>
     */
    @Test
    void test_supersededVersionRetired() {
        long secondId = approvedInFuture();
        long thirdId = createSubmitted(version()).id("versionId");
        approve(3, CHECKER_ID).assertSuccess(200).assertText("status", RuleVersionStatus.ACTIVE.getCode());
        Instant thirdFrom = testMapper.selectRuleVersion(thirdId).getEffectiveFrom();
        Instant secondFrom = makeDue(secondId, thirdFrom.minus(1, ChronoUnit.MINUTES));

        ActivateDueRuleVersionsResponse result = job.runOnce();

        assertAll(() -> assertEquals(0, result.getActivatedCount()), () -> assertEquals(1, result.getRetiredCount()),
            () -> assertEquals(0, result.getFailedCount()));
        AccountingRuleBundleVer second = testMapper.selectRuleVersion(secondId);
        AccountingRuleBundleVer third = testMapper.selectRuleVersion(thirdId);
        assertAll(() -> assertEquals(RuleVersionStatus.RETIRED, second.getStatus()),
            () -> assertEquals(secondFrom, second.getEffectiveTo()),
            () -> assertEquals(RuleVersionStatus.ACTIVE, third.getStatus()), () -> assertNull(third.getEffectiveTo()));
        assertPointer(thirdId, 1L);
        ActivateDueRuleVersionsResponse next = job.runOnce();
        assertAll(() -> assertEquals(0, next.getRetiredCount()), () -> assertEquals(0, next.getFailedCount()));
    }

    /** 经 HTTP 创建、提交并批准一个明天生效的第 2 版（APPROVED），返回版本主键。 */
    long approvedInFuture() {
        Instant future = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.DAYS);
        long versionId = createSubmitted(RuleVersionRequestBuilder.template(future)).id("versionId");
        approve(2, CHECKER_ID).assertSuccess(200).assertText("status", RuleVersionStatus.APPROVED.getCode());
        return versionId;
    }

    /** 把版本生效时间改到给定的过去时点，返回该时点。 */
    Instant makeDue(long versionId, Instant effectiveFrom) {
        testMapper.updateRuleVersionEffectiveFrom(versionId, effectiveFrom);
        return effectiveFrom;
    }
}
