package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.CandidateBuilder;
import com.paytech.risk.test.checker.decision.TestReleaseDetailResponseChecker;
import com.paytech.risk.test.mocker.decision.CandidateMocker;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

/**
 * TEST Candidate 详情查询
 * <li>正常流程：DRAFT、已开始未完成的 VALIDATING、八项通过的 VALIDATING 与 REJECTED 四种形态，返回冻结组成、状态审计与固定顺序检查。</li>
 * <li>未找到：发布 ID 未登记返回 404。</li>
 * <li>参数：路径发布 ID 非规范正 BIGINT、携带任何 Query 或 GET Body 时在查询前拒绝（400）。</li>
 * <li>闭包损坏：发布摘要、决策点版本摘要、规则内容摘要、绑定、状态链或八项检查任一不完整时失败关闭（500）。</li>
 *
 * @see DecisionReleaseManagementApi#getCandidate(String, String, String)
 */
class TestReleaseDetailApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-releases/v1/candidates/%s";

    @Autowired
    private CandidateMocker candidates;

    /** 刚创建的 DRAFT：一条创建事件、无检查，三态结果为 null，不等待 Shadow。 */
    @Test
    void test_normalFlow_draft() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        read(candidate.releaseId()).assertCandidate(candidate, null, false);
    }

    /** 校验已开始但尚未写入检查结果：两条状态事件、无检查。 */
    @Test
    void test_normalFlow_validationStarted() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationStarted());

        read(candidate.releaseId()).assertCandidate(candidate, null, false);
    }

    /** 八项全部通过并保持 VALIDATING：三态结果为 true，等待后续 Shadow 能力。 */
    @Test
    void test_normalFlow_validationPassed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationPassed());

        read(candidate.releaseId()).assertCandidate(candidate, true, true);
    }

    /** 检查失败后 REJECTED：三条状态事件、AST_TYPES 失败摘要，三态结果为 false。 */
    @Test
    void test_normalFlow_rejected() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.rejected());

        read(candidate.releaseId()).assertCandidate(candidate, false, false);
    }

    /** 同一组成上的多个 Candidate 互不混入审计事实。 */
    @Test
    void test_normalFlow_candidatesOfSameCompositionAreIsolated() {
        MockedCandidate first = candidates.mockCandidate(CandidateBuilder.rejected());
        MockedCandidate second = candidates.mockCandidate(first.composition(), CandidateBuilder.draft().orderedStringRule());

        read(second.releaseId()).assertCandidate(second, null, false);
        read(first.releaseId()).assertCandidate(first, false, false);
    }

    /** 发布 ID 未登记。 */
    @Test
    void test_noDataFound() {
        candidates.mockCandidate(CandidateBuilder.draft());

        read(999_999_999_999_999_999L).assertNotFound();
    }

    /** 发布内容摘要与冻结闭包重算结果不符时失败关闭。 */
    @Test
    void test_businessRuleViolation_releaseHashMismatchFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.corruptReleaseHash(candidate);

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 决策点版本内容摘要漂移时失败关闭。 */
    @Test
    void test_businessRuleViolation_pointVersionHashDriftFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationPassed());
        candidates.corruptPointVersionHash(candidate.composition());

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 规则集版本内容摘要漂移时失败关闭。 */
    @Test
    void test_businessRuleViolation_ruleSetHashDriftFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.corruptRuleSetHash(candidate.composition(), "0".repeat(64));

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 决策点版本失去唯一规则集绑定时失败关闭。 */
    @Test
    void test_businessRuleViolation_missingBindingFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.removeBinding(candidate.composition());

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** VALIDATING 根缺少开始事件，状态链与根版本不一致。 */
    @Test
    void test_businessRuleViolation_brokenStateChainFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationStarted());
        candidates.removeAudit(candidate.stateChanges().get(1));

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 八项检查只剩七项时不返回部分证据。 */
    @Test
    void test_businessRuleViolation_partialStaticChecksFailClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationPassed());
        candidates.removeAudit(candidate.staticChecks().getLast());

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 首期之外的发布状态（SHADOWING）不可见，失败关闭。 */
    @Test
    void test_businessRuleViolation_nonCandidateStatusFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(
            CandidateBuilder.draft().status(DecisionReleaseStatus.SHADOWING));

        read(candidate.releaseId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 路径中的发布 ID 格式非法。 */
    @ParameterizedTest(name = "releaseId={0}")
    @MethodSource("invalidReleaseIds")
    void test_invalidParameter_releaseId(String releaseId) {
        invoke(headers(), PATH.formatted(releaseId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 详情不声明 Query，未知、重复或空参数均在查询前拒绝。 */
    @ParameterizedTest(name = "query={0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter_query(String query) {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        invoke(headers(), PATH.formatted(candidate.releaseId()) + "?" + query).assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        String requestId = nextRequestId();

        new TestReleaseDetailResponseChecker(http.exchange(HttpMethod.GET, PATH.formatted(candidate.releaseId()),
            headers(requestId), "{}"), objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<String> invalidQueries() {
        return Stream.of("status=DRAFT", "page=1&page=1", "page=");
    }

    static Stream<String> invalidReleaseIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private TestReleaseDetailResponseChecker read(long releaseId) {
        return invoke(headers(), PATH.formatted(releaseId));
    }

    private TestReleaseDetailResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new TestReleaseDetailResponseChecker(http.get(uri, headers), objectMapper, requestId);
    }

    private static HttpHeaders headers() {
        return headers(nextRequestId());
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-release-detail-" + UUID.randomUUID();
    }
}
