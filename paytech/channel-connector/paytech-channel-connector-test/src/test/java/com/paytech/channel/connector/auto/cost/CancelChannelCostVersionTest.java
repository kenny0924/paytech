package com.paytech.channel.connector.auto.cost;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.cost.ChannelCostPlanApi;
import com.paytech.channel.connector.api.cost.request.CancelChannelCostVersionRequestBody;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.ext.job.ChannelCostVersionActivationJobHandler;
import com.paytech.channel.connector.test.base.AbstractChannelCostApiTest;

/**
 * 作废渠道成本版本：未到生效时间的 APPROVED 版本作废为 INACTIVE（空区间）并写 CANCEL 变更记录；ACTIVE、PENDING_APPROVAL、
 * 已到生效时间的 APPROVED 与重复作废被拒；方案不存在被拒；作废后到期切换 Job 不再切换；字段校验 422。
 *
 * @see ChannelCostPlanApi#cancelChannelCostVersion(String, String, CancelChannelCostVersionRequestBody)
 */
class CancelChannelCostVersionTest extends AbstractChannelCostApiTest {
    /** 作废人。 */
    private static final String OPERATOR_ID = "81003";

    /** 作废原因。 */
    private static final String REASON = "渠道费率文件更正，改由新版本发布";

    @Autowired
    private ChannelCostVersionActivationJobHandler job;

    /** 本类公共配置：标准 Channel / MID 与 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        mockStandardChannel();
        createStandardPlan();
    }

    /** 未到期 APPROVED：作废为 INACTIVE、失效时间等于生效时间，方案不变，写入一条 CANCEL 变更记录且可经查询接口读到。 */
    @Test
    void test_normalFlow() {
        Instant effectiveFrom = tomorrow();
        createApproved(version(effectiveFrom)).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());

        cancel(1, OPERATOR_ID, REASON).assertSuccess(200)
            .assertText("status", ChannelCostVersionStatus.INACTIVE.getCode())
            .assertInstant("effectiveFrom", effectiveFrom).assertInstant("effectiveTo", effectiveFrom);

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
        long versionId = testMapper.selectVersionId(PLAN_CODE, 1);
        List<Map<String, Object>> changes =
            testMapper.selectConfigChanges(ChangeTargetType.CHANNEL_COST_VERSION.getCode(), versionId);
        assertEquals(1, changes.size(), "变更记录数");
        Map<String, Object> change = changes.get(0);
        assertAll(() -> assertEquals(ChangeType.CANCEL.getCode(), change.get("change_type")),
            () -> assertEquals(ChannelCostVersionStatus.APPROVED.getCode(), change.get("from_status")),
            () -> assertEquals(ChannelCostVersionStatus.INACTIVE.getCode(), change.get("to_status")),
            () -> assertEquals(Long.parseLong(OPERATOR_ID), ((Number)change.get("operator_id")).longValue()),
            () -> assertEquals(REASON, change.get("reason")));
        var queried = get("/channel-connector/v1/config-changes?targetType="
            + ChangeTargetType.CHANNEL_COST_VERSION.getCode() + "&targetId=" + versionId).assertPage(1, 10, 1, 1);
        assertEquals(ChangeType.CANCEL.getCode(), queried.item(0).path("changeType").asString());
        assertEquals(OPERATOR_ID, queried.item(0).path("operatorId").asString());
    }

    /** ACTIVE 版本不能作废：业务拒绝，提示含方案代码、版本号与当前状态，不写变更记录。 */
    @Test
    void test_activeVersionRejected() {
        createApproved(version());

        cancel(1, OPERATOR_ID, REASON).assertBusinessRejected(PLAN_CODE).assertDetailContains("版本 1")
            .assertDetailContains(ChannelCostVersionStatus.ACTIVE.getCode());

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** PENDING_APPROVAL 版本不能作废。 */
    @Test
    void test_pendingVersionRejected() {
        createSubmitted(version(tomorrow()));

        cancel(1, OPERATOR_ID, REASON).assertBusinessRejected(ChannelCostVersionStatus.PENDING_APPROVAL.getCode());

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 已到生效时间但 Job 尚未切换的 APPROVED 版本不能作废，保持 APPROVED。 */
    @Test
    void test_dueApprovedRejected() {
        createApproved(version(tomorrow()));
        testMapper.rewriteVersionWindow(PLAN_CODE, 1, ChannelCostVersionStatus.APPROVED.getCode(), minutesAgo(1), null);

        cancel(1, OPERATOR_ID, REASON).assertBusinessRejected("已到生效时间")
            .assertDetailContains(ChannelCostVersionStatus.APPROVED.getCode());

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
        assertEquals(0, testMapper.countConfigChangeRows());
    }

    /** 方案或版本不存在：业务拒绝。 */
    @Test
    void test_noDataFound() {
        cancel("UNKNOWN_PLAN", 1, OPERATOR_ID, REASON).assertBusinessRejected("渠道成本方案不存在");
        cancel(9, OPERATOR_ID, REASON).assertBusinessRejected("渠道成本版本不存在");
    }

    /** 重复作废：第二次按当前 INACTIVE 状态拒绝，只保留第一次的变更记录。 */
    @Test
    void test_repeatedCancelRejected() {
        createApproved(version(tomorrow()));
        cancel(1, OPERATOR_ID, REASON).assertSuccess(200);

        cancel(1, OPERATOR_ID, REASON).assertBusinessRejected(ChannelCostVersionStatus.INACTIVE.getCode());

        assertEquals(1, testMapper.countConfigChangeRows());
    }

    /** 作废后时间推进到原生效时间：到期切换 Job 不选中作废版本，当前版本与方案不变。 */
    @Test
    void test_cancelledVersionNotActivatedByJob() {
        createApproved(version(minutesAgo(10)));
        createApproved(version(tomorrow()));
        cancel(2, OPERATOR_ID, REASON).assertSuccess(200);
        Instant passed = minutesAgo(2);
        testMapper.rewriteVersionWindow(PLAN_CODE, 2, ChannelCostVersionStatus.INACTIVE.getCode(), passed, passed);

        var result = job.runOnce();

        assertEquals(0, result.getActivatedCount() + result.getSkippedCount() + result.getFailedCount());
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("effectiveTo", null);
        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 作废人与原因字段格式非法：422 字段错误，不改变版本。 */
    @Test
    void test_invalidParameter() {
        createApproved(version(tomorrow()));

        cancel(1, "0", REASON).assertProblem(422, "VALIDATION_ERROR").assertFieldError("operatorId");
        cancel(1, OPERATOR_ID, "").assertProblem(422, "VALIDATION_ERROR").assertFieldError("reason");
        cancel(1, OPERATOR_ID, "原".repeat(513)).assertProblem(422, "VALIDATION_ERROR").assertFieldError("reason");

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.APPROVED.getCode());
    }
}
