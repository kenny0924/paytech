package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 执行发布的 HTTP 响应 Checker
 * <li>核对发布状态、静态检查结果与最近一次影子批次汇总。</li>
 *
 * @author Kenny
 */
public final class ExecutionReleaseResponseChecker extends AbstractHttpResponseChecker<ExecutionReleaseResponseChecker> {

    public ExecutionReleaseResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected ExecutionReleaseResponseChecker self() {
        return this;
    }

    /**
     * 发布身份与状态
     *
     * @param status 期望的发布状态 Code
     * @param pointCode 决策点编码
     * @param environment 环境 Code
     * @return 发布 ID
     */
    public long assertRelease(String status, String pointCode, String environment) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("执行发布",
            () -> assertTrue(body.path("releaseId").asString().matches("[1-9][0-9]{0,18}"), "releaseId"),
            () -> assertEquals(status, body.path("status").asString(), "status"),
            () -> assertEquals(pointCode, body.path("pointCode").asString(), "pointCode"),
            () -> assertEquals(environment, body.path("environment").asString(), "environment"),
            () -> assertTrue(body.path("contentHash").asString().matches("[0-9a-f]{64}"), "contentHash"));
        return Long.parseLong(body.path("releaseId").asString());
    }

    /**
     * 八项静态检查按工程顺序给出，检查状态与期望一致
     *
     * @param failedCodes 期望失败的检查项；其余全部通过
     * @return 当前 Checker
     */
    public ExecutionReleaseResponseChecker assertChecks(List<StaticCheckCode> failedCodes) {
        List<String> codes = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        body().path("checks").forEach(check -> {
            codes.add(check.path("checkCode").asString());
            if (!StaticCheckStatus.PASSED.getCode().equals(check.path("checkStatus").asString())) {
                failed.add(check.path("checkCode").asString());
            }
        });
        assertAll("静态检查",
            () -> assertEquals(Arrays.stream(StaticCheckCode.values()).map(StaticCheckCode::getCode).toList(),
                codes, "检查项"),
            () -> assertEquals(failedCodes.stream().map(StaticCheckCode::getCode).toList(), failed, "失败项"));
        return this;
    }

    /**
     * 没有静态检查结果（尚未校验）
     *
     * @return 当前 Checker
     */
    public ExecutionReleaseResponseChecker assertNoChecks() {
        assertTrue(body().path("checks").isEmpty(), "未校验的发布没有检查结果");
        return this;
    }

    /**
     * 激活或替代时间
     *
     * @param activated 是否已有激活时间
     * @param superseded 是否已有替代时间
     * @return 当前 Checker
     */
    public ExecutionReleaseResponseChecker assertTimes(boolean activated, boolean superseded) {
        JsonNode body = body();
        assertAll("状态时间",
            () -> assertEquals(activated, !body.path("activatedTime").isNull() && !body.path("activatedTime").isMissingNode(),
                "activatedTime"),
            () -> assertEquals(superseded,
                !body.path("supersededTime").isNull() && !body.path("supersededTime").isMissingNode(), "supersededTime"));
        return this;
    }

    /** 当前影子批次状态；没有批次时为空。 */
    public String shadowRunStatus() {
        JsonNode run = body().path("shadowRun");
        return run.isNull() || run.isMissingNode() ? null : run.path("status").asString();
    }

    /**
     * 已完成的影子批次汇总
     *
     * @param baselineReleaseId 基线发布
     * @param sampleCount 已回放样本数
     * @param checkStatus 技术门槛 PASSED 或 FAILED
     * @return 影子批次 ID
     */
    public long assertShadowRun(long baselineReleaseId, int sampleCount, String checkStatus) {
        JsonNode run = body().path("shadowRun");
        assertFalse(run.isNull() || run.isMissingNode(), "shadowRun");
        assertAll("影子批次",
            () -> assertEquals(ShadowRunStatus.COMPLETED.getCode(), run.path("status").asString(), "status"),
            () -> assertEquals(Long.toString(baselineReleaseId), run.path("baselineReleaseId").asString(), "baselineReleaseId"),
            () -> assertEquals(sampleCount, run.path("sampleCount").asInt(), "sampleCount"),
            () -> assertEquals(0, run.path("failedCount").asInt(), "failedCount"),
            () -> assertEquals(0, run.path("timedOutCount").asInt(), "timedOutCount"),
            () -> assertEquals(checkStatus, run.path("checkStatus").asString(), "checkStatus"));
        return Long.parseLong(run.path("shadowRunId").asString());
    }
}
