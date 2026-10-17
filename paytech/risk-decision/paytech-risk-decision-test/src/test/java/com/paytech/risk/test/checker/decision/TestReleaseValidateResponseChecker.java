package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.checker.decision.TestReleaseDetailResponseChecker.assertCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.test.builder.decision.CandidateBuilder.StaticCheckSpec;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 静态校验 TEST Candidate 的 HTTP 响应 Checker
 * <li>根字段核对原 Candidate 的不可变身份与摘要；状态、版本与八项检查按用例约定的期望核对。</li>
 *
 * @author Kenny
 */
public final class TestReleaseValidateResponseChecker
    extends AbstractHttpResponseChecker<TestReleaseValidateResponseChecker> {

    public TestReleaseValidateResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected TestReleaseValidateResponseChecker self() {
        return this;
    }

    /** 成功响应：状态、版本、三态结果与固定顺序的八项检查。 */
    public TestReleaseValidateResponseChecker assertValidated(MockedCandidate candidate, DecisionReleaseStatus status,
        int version, List<StaticCheckSpec> checks) {
        assertStatus(200);
        JsonNode body = body();
        boolean allPassed = checks.stream().allMatch(check -> check.checkStatus() == StaticCheckStatus.PASSED);
        assertAll("校验结果 " + candidate.releaseId(),
            () -> assertEquals(Long.toString(candidate.releaseId()), body.path("releaseId").asString()),
            () -> assertEquals(candidate.releaseNo(), body.path("releaseNo").asInt()),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), body.path("environmentCode").asString()),
            () -> assertEquals(status.getCode(), body.path("status").asString()),
            () -> assertEquals(version, body.path("version").asInt()),
            () -> assertEquals(candidate.contentHash(), body.path("contentHash").asString()),
            () -> assertTrue(body.path("allStaticChecksPassed").isBoolean(), "三态结果完成后必须为布尔值"),
            () -> assertEquals(allPassed, body.path("allStaticChecksPassed").asBoolean()),
            () -> assertEquals(8, body.size(), "校验响应字段数量"));
        JsonNode items = body.path("checks");
        assertEquals(checks.size(), items.size(), "检查数量不符");
        for (int i = 0; i < checks.size(); i++) {
            StaticCheckSpec expected = checks.get(i);
            JsonNode actual = items.get(i);
            assertAll("检查 " + expected.checkCode(),
                () -> assertEquals(expected.checkCode().getCode(), actual.path("checkCode").asString()),
                () -> assertEquals(expected.checkStatus().getCode(), actual.path("checkStatus").asString()),
                () -> assertEquals(expected.severity().getCode(), actual.path("severity").asString()),
                () -> assertCode(expected.errorCode(), actual.path("errorCode")),
                () -> assertCode(expected.errorSummary(), actual.path("errorSummary")),
                () -> assertEquals(5, actual.size(), "检查字段数量"));
        }
        return this;
    }

    /** 发布不存在。 */
    public TestReleaseValidateResponseChecker assertNotFound() {
        return assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 原始响应体，用于重复调用逐字节比较。 */
    public String rawBody() {
        return new String(result.getResponseBody(), StandardCharsets.UTF_8);
    }
}
