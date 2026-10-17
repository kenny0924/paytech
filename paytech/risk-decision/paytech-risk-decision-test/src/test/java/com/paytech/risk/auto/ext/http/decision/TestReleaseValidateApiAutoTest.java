package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionReleaseManagementApi;
import com.paytech.risk.decision.api.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.CandidateBuilder;
import com.paytech.risk.test.checker.decision.ReleaseStoreChecker;
import com.paytech.risk.test.checker.decision.TestReleaseValidateResponseChecker;
import com.paytech.risk.test.mocker.decision.CandidateMocker;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

/**
 * 静态校验 TEST Candidate
 * <li>正常流程：DRAFT/version=1 开始唯一一轮八项检查；全通过保持 VALIDATING/version=2，任一失败进入 REJECTED/version=3，开始、检查与拒绝事实依次落库。</li>
 * <li>恢复与幂等：已开始未完成时原操作者以原 expectedVersion 完成原校验，不新增开始事件；已完成时返回原结果且不写入；并发调用只产生一轮检查。</li>
 * <li>未找到：发布 ID 未登记返回 404。</li>
 * <li>业务规则：expectedVersion 不符或非原校验操作者返回 409 CONCURRENT_REQUEST，根与审计不变。</li>
 * <li>完整性：发布摘要、决策点版本摘要或已有检查不完整时失败关闭（500），不写入。</li>
 * <li>参数：路径 ID、Body 缺键、未知字段、重复键、非对象、类型错误（含小数与数字文本）或携带 Query 返回 400；expectedVersion 为空或非正数返回 422；非 JSON 媒体类型返回 415。</li>
 *
 * @see DecisionReleaseManagementApi#validateCandidate(String, String, String, ValidateTestReleaseRequestBody)
 */
class TestReleaseValidateApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-releases/v1/candidates/%s/validate";

    private static final long ACTOR = CandidateBuilder.ACTOR_ID;

    private static final long OTHER_ACTOR = 70002L;

    @Autowired
    private CandidateMocker candidates;

    @Autowired
    private ReleaseStoreChecker store;

    /** 八项全部通过：保持 VALIDATING/version=2，写入开始事件与八项 PASSED 检查。 */
    @Test
    void test_normalFlow_allChecksPassed() {
        CandidateBuilder builder = CandidateBuilder.draft();
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());

        Instant before = now();
        validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks);
        store.assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks, ACTOR, before);
    }

    /** AST_TYPES 失败：进入 REJECTED/version=3，写入开始、八项检查与拒绝事件。 */
    @Test
    void test_normalFlow_staticCheckFailureRejects() {
        CandidateBuilder builder = CandidateBuilder.draft().orderedStringRule();
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());

        Instant before = now();
        validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3, checks);
        store.assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3, checks, ACTOR, before);
    }

    /** 开始后中断：原操作者以原 expectedVersion 完成原校验，沿用原开始事件。 */
    @Test
    void test_normalFlow_resumeStartedValidation() {
        CandidateBuilder builder = CandidateBuilder.validationStarted();
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());

        Instant before = now();
        validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks);
        store.assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks, ACTOR, before);
    }

    /** 开始后中断且检查失败：恢复完成时以原开始事件的操作者与触发标识写入拒绝事件。 */
    @Test
    void test_normalFlow_resumeStartedValidationRejects() {
        CandidateBuilder builder = CandidateBuilder.validationStarted().orderedStringRule().validatedBy(70003L);
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());

        Instant before = now();
        validate(candidate, 70003L, 1).assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3, checks);
        store.assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3, checks, 70003L, before);
    }

    /** 已完成且全通过：返回原八项结果，不写入。 */
    @Test
    void test_edgeCase_completedValidationReturnsOriginalResult() {
        CandidateBuilder builder = CandidateBuilder.validationPassed();
        MockedCandidate candidate = candidates.mockCandidate(builder);

        validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2,
            builder.checks(candidate.composition().ruleId()));
        store.assertUnchanged(candidate);
    }

    /** 已完成且被拒绝：返回原 REJECTED 结果与失败摘要，不写入。 */
    @Test
    void test_edgeCase_rejectedValidationReturnsOriginalResult() {
        CandidateBuilder builder = CandidateBuilder.rejected();
        MockedCandidate candidate = candidates.mockCandidate(builder);

        validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3,
            builder.checks(candidate.composition().ruleId()));
        store.assertUnchanged(candidate);
    }

    /** 同一操作者重复调用：第二次返回与第一次逐字节相同的结果，不新增审计。 */
    @Test
    void test_edgeCase_repeatedValidateReturnsSameResult() {
        CandidateBuilder builder = CandidateBuilder.draft().orderedStringRule();
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());
        Instant before = now();
        String first = validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3,
            checks).rawBody();

        String second = validate(candidate, ACTOR, 1).assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3,
            checks).rawBody();

        assertEquals(first, second);
        store.assertValidated(candidate, DecisionReleaseStatus.REJECTED, 3, checks, ACTOR, before);
    }

    /** 并发校验同一 DRAFT：成功的调用返回同一结果，落库只有一轮开始事件与八项检查。 */
    @Test
    void test_edgeCase_concurrentValidateCompletesOnce() throws Exception {
        CandidateBuilder builder = CandidateBuilder.draft();
        MockedCandidate candidate = candidates.mockCandidate(builder);
        var checks = builder.checks(candidate.composition().ruleId());
        Instant before = now();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TestReleaseValidateResponseChecker> first = executor.submit(() -> awaitAndValidate(start, candidate));
            Future<TestReleaseValidateResponseChecker> second = executor.submit(() -> awaitAndValidate(start, candidate));
            start.countDown();
            List<TestReleaseValidateResponseChecker> responses = List.of(first.get(20, TimeUnit.SECONDS),
                second.get(20, TimeUnit.SECONDS));

            List<String> bodies = responses.stream().filter(response -> response.status() == 200)
                .map(response -> response.assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks)
                    .rawBody()).distinct().toList();
            assertEquals(1, bodies.size(), "至少一次成功且成功结果一致");
            responses.stream().filter(response -> response.status() != 200)
                .forEach(response -> response.assertProblem(409, CONCURRENT_REQUEST.getCode()));
        } finally {
            executor.shutdownNow();
        }
        store.assertValidated(candidate, DecisionReleaseStatus.VALIDATING, 2, checks, ACTOR, before);
    }

    /** 发布 ID 未登记。 */
    @Test
    void test_noDataFound() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        invoke(PATH.formatted("999999999999999999"), headers(ACTOR, nextRequestId()), new ValidateTestReleaseRequestBody(1))
            .assertNotFound();
        store.assertUnchanged(candidate);
    }

    /** DRAFT 的 expectedVersion 与当前版本不符。 */
    @Test
    void test_businessRuleViolation_draftVersionMismatch() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        validate(candidate, ACTOR, 2).assertProblem(409, CONCURRENT_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** 恢复时 expectedVersion 不是原值 1。 */
    @Test
    void test_businessRuleViolation_resumeWithDifferentExpectedVersion() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationStarted());

        validate(candidate, ACTOR, 2).assertProblem(409, CONCURRENT_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** 非原校验操作者不能恢复已开始的校验。 */
    @Test
    void test_businessRuleViolation_resumeByDifferentActor() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.validationStarted());

        validate(candidate, OTHER_ACTOR, 1).assertProblem(409, CONCURRENT_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** 非原校验操作者不能读取已完成的校验结果。 */
    @Test
    void test_businessRuleViolation_completedResultByDifferentActor() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.rejected());

        validate(candidate, OTHER_ACTOR, 1).assertProblem(409, CONCURRENT_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** 发布内容摘要与冻结闭包不符时不开始校验。 */
    @Test
    void test_businessRuleViolation_releaseHashMismatchFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        MockedCandidate corrupted = candidates.corruptReleaseHash(candidate);

        validate(candidate, ACTOR, 1).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertUnchanged(corrupted);
    }

    /** 决策点版本摘要漂移时不开始校验。 */
    @Test
    void test_businessRuleViolation_pointVersionHashDriftFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.corruptPointVersionHash(candidate.composition());

        validate(candidate, ACTOR, 1).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertUnchanged(candidate);
    }

    /** 已有检查不完整（七项）时不补写、不返回部分结果。 */
    @Test
    void test_businessRuleViolation_partialStaticChecksFailClosed() {
        MockedCandidate passed = candidates.mockCandidate(CandidateBuilder.validationPassed());
        candidates.removeAudit(passed.staticChecks().getLast());
        MockedCandidate candidate = passed.withStaticChecks(passed.staticChecks().subList(0, 7));

        validate(candidate, ACTOR, 1).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertUnchanged(candidate);
    }

    /** 路径中的发布 ID 格式非法。 */
    @ParameterizedTest(name = "releaseId={0}")
    @MethodSource("invalidReleaseIds")
    void test_invalidParameter_releaseId(String releaseId) {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        invoke(PATH.formatted(releaseId), headers(ACTOR, nextRequestId()), new ValidateTestReleaseRequestBody(1))
            .assertProblem(400, INVALID_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** Body 缺少必填键、类型错误或不是单个 JSON 对象。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableBodies")
    void test_invalidParameter(String scenario, String rawBody) {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        validate(candidate, rawBody).assertProblem(400, INVALID_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** 写接口不声明 Query，携带任何查询参数均在业务处理前拒绝。 */
    @Test
    void test_invalidParameter_query() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        invoke(PATH.formatted(candidate.releaseId()) + "?expectedVersion=1", headers(ACTOR, nextRequestId()),
            new ValidateTestReleaseRequestBody(1)).assertProblem(400, INVALID_REQUEST.getCode());
        store.assertUnchanged(candidate);
    }

    /** expectedVersion 为空或非正数时返回字段级校验错误。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidExpectedVersions")
    void test_invalidParameter_expectedVersion(String scenario, String rawBody) {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());

        validate(candidate, rawBody).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError("expectedVersion");
        store.assertUnchanged(candidate);
    }

    /** 非 JSON 媒体类型在进入合同前拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        String requestId = nextRequestId();
        HttpHeaders headers = headers(ACTOR, requestId);
        headers.setContentType(MediaType.TEXT_PLAIN);

        invoke(PATH.formatted(candidate.releaseId()), headers, "{\"expectedVersion\":1}")
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());
        store.assertUnchanged(candidate);
    }

    static Stream<String> invalidReleaseIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    static Stream<Arguments> unreadableBodies() {
        return Stream.of(Arguments.of("空对象", "{}"), Arguments.of("expectedVersion 为文本", "{\"expectedVersion\":\"one\"}"),
            Arguments.of("expectedVersion 超过 INT", "{\"expectedVersion\":2147483648}"),
            Arguments.of("expectedVersion 为布尔值", "{\"expectedVersion\":true}"),
            Arguments.of("expectedVersion 为小数", "{\"expectedVersion\":1.5}"),
            Arguments.of("expectedVersion 为整值小数", "{\"expectedVersion\":1.0}"),
            Arguments.of("expectedVersion 为数字文本", "{\"expectedVersion\":\"1\"}"),
            Arguments.of("expectedVersion 重复键", "{\"expectedVersion\":2,\"expectedVersion\":1}"),
            Arguments.of("含未知字段", "{\"expectedVersion\":1,\"releaseId\":\"1\"}"),
            Arguments.of("非 JSON", "expectedVersion=1"), Arguments.of("JSON 数组", "[1]"),
            Arguments.of("截断 JSON", "{\"expectedVersion\":"));
    }

    static Stream<Arguments> invalidExpectedVersions() {
        return Stream.of(Arguments.of("为 null", "{\"expectedVersion\":null}"),
            Arguments.of("为 0", "{\"expectedVersion\":0}"), Arguments.of("为负数", "{\"expectedVersion\":-1}"));
    }

    private TestReleaseValidateResponseChecker awaitAndValidate(CountDownLatch start, MockedCandidate candidate)
        throws InterruptedException {
        assertTrue(start.await(5, TimeUnit.SECONDS), "并发校验未收到起跑信号");
        return validate(candidate, ACTOR, 1);
    }

    private TestReleaseValidateResponseChecker validate(MockedCandidate candidate, long actor, int expectedVersion) {
        return invoke(PATH.formatted(candidate.releaseId()), headers(actor, nextRequestId()),
            new ValidateTestReleaseRequestBody(expectedVersion));
    }

    private TestReleaseValidateResponseChecker validate(MockedCandidate candidate, String rawBody) {
        return invoke(PATH.formatted(candidate.releaseId()), headers(ACTOR, nextRequestId()), rawBody);
    }

    private TestReleaseValidateResponseChecker invoke(String path, HttpHeaders headers, Object body) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new TestReleaseValidateResponseChecker(http.post(path, headers, body), objectMapper, requestId);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(long actor, String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(actor));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-release-validate-" + UUID.randomUUID();
    }
}
