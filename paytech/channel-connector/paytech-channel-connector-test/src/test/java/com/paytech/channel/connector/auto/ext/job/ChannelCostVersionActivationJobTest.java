package com.paytech.channel.connector.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostActivationResult;
import com.paytech.channel.connector.ext.job.ChannelCostVersionActivationJobHandler;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;

/**
 * 渠道成本版本到期切换：显式触发生产 XXL-Job Handler，经 ActivateDueChannelCostVersionsProcessor、按方案加锁的
 * ActivateChannelCostVersionProcessor 与 ChannelCostPlanService 读写本地 MySQL / Redis
 * <li>到期的 APPROVED 版本成为 ACTIVE、原当前版本 INACTIVE 且 effective_to = 新版本生效时间、方案指针与乐观锁版本推进；
 * 未到期不动；重复执行幂等；生效时间不晚于当前版本的到期版本计为失败并保持 APPROVED。</li>
 * <li>“到期”由 TestMapper 把已批准版本的生效时间改到过去来模拟时间推进，不修改生产时钟。</li>
 *
 * @see ChannelCostVersionActivationJobHandler#runOnce()
 */
class ChannelCostVersionActivationJobTest extends AbstractChannelCostApiTest {
    @Autowired
    private ChannelCostVersionActivationJobHandler job;

    /** 本类公共配置：标准 Channel / MID 与 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
        createStandardPlan();
    }

    /** 到期切换：v1 当前、v2 APPROVED 到期后成为 ACTIVE，v1 INACTIVE 且失效时间等于 v2 生效时间。 */
    @Test
    void test_dueVersionActivated() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow())).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
        Instant secondFrom = minutesAgo(2);
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.APPROVED.getCode(), secondFrom, null);

        ChannelCostActivationResult result = job.runOnce();

        assertResult(result, 1, 0, 0);
        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("effectiveTo", null);
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode())
            .assertInstant("effectiveTo", secondFrom);
        getPlan(PLAN_CODE).assertSuccess(200).assertText("currentCostVer", "2");
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 2, 2L);
    }

    /** 首个版本到期：方案 DRAFT → ACTIVE，当前版本为 v1，无被替代版本。 */
    @Test
    void test_firstVersionActivatesPlan() {
        createApproved(version(tomorrow()));
        testMapper.rewriteVersionWindow(PLAN_CODE, 1, ChannelCostVersionStatus.APPROVED.getCode(), minutesAgo(1),
            null);

        assertResult(job.runOnce(), 1, 0, 0);

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 未到期：APPROVED 版本、当前版本与方案均不变。 */
    @Test
    void test_notDueUnchanged() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow()));

        assertResult(job.runOnce(), 0, 0, 0);

        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("effectiveTo", null);
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 重复执行：第二轮没有到期 APPROVED 版本，状态、失效时间与方案乐观锁版本不再变化。 */
    @Test
    void test_repeatedRunIdempotent() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow()));
        Instant secondFrom = minutesAgo(2);
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.APPROVED.getCode(), secondFrom, null);
        assertResult(job.runOnce(), 1, 0, 0);

        assertResult(job.runOnce(), 0, 0, 0);

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode())
            .assertInstant("effectiveTo", secondFrom);
        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 2, 2L);
    }

    /** 同一方案两个版本同时到期：按生效时间依次切换，最终当前版本为最晚生效者，区间首尾相接。 */
    @Test
    void test_multipleDueVersionsInOrder() {
        createApproved(version(minutesAgo(30)));
        createApproved(version(tomorrow()));
        Instant secondFrom = minutesAgo(20);
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.APPROVED.getCode(), secondFrom, null);
        createApproved(version(tomorrow()));
        Instant thirdFrom = minutesAgo(10);
        testMapper.rewriteVersionWindow(PLAN_CODE, 3, ChannelCostVersionStatus.APPROVED.getCode(), thirdFrom, null);

        assertResult(job.runOnce(), 2, 0, 0);

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode())
            .assertInstant("effectiveTo", secondFrom);
        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode())
            .assertInstant("effectiveTo", thirdFrom);
        getVersion(3).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("effectiveTo", null);
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 3, 3L);
    }

    /** 到期版本生效时间不晚于当前版本：计为失败、保持 APPROVED，当前版本不变。 */
    @Test
    void test_earlierDueVersionFails() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow()));
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.APPROVED.getCode(), minutesAgo(20),
            null);

        assertResult(job.runOnce(), 0, 0, 1);

        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 断言一轮结果的三项计数。 */
    static void assertResult(ChannelCostActivationResult result, int activated, int skipped, int failed) {
        assertAll(() -> assertEquals(activated, result.getActivatedCount(), "activated"),
            () -> assertEquals(skipped, result.getSkippedCount(), "skipped"),
            () -> assertEquals(failed, result.getFailedCount(), "failed"));
    }
}
