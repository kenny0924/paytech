package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionReleaseManagementApi;
import com.paytech.risk.decision.api.request.CreateTestReleaseRequestBody;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.CandidateBuilder;
import com.paytech.risk.test.builder.decision.CandidateBuilder.CompositionFacts;
import com.paytech.risk.test.checker.decision.ReleaseStoreChecker;
import com.paytech.risk.test.checker.decision.TestReleaseCreateResponseChecker;
import com.paytech.risk.test.mocker.decision.CandidateMocker;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

/**
 * 创建 TEST Candidate
 * <li>正常流程：以冻结规则版本及其旧发布组成创建 DRAFT/version=1 Candidate，发布编号为全表最大值加一，根与初始审计同事务保存；
 * 每次有效调用新建一个 Candidate（不占用生效槽），历史冻结版本同样可用。</li>
 * <li>未找到：规则集版本未登记返回 404。</li>
 * <li>业务规则：预期内容摘要不符返回 409 CONCURRENT_REQUEST；规则集已删除返回 409 RISK_RULE_SET_DELETED；
 * 草稿版本、无旧发布组成、统一变量格式不具备资格（400 BUSINESS_CHECK_FAILED）。均不写入任何发布。</li>
 * <li>完整性：存储摘要漂移、全表存在非规范发布编号或编号达到上限时失败关闭（500），不写入。</li>
 * <li>参数：Body 缺字段、未知字段、重复键、非 JSON、非对象、ID 为 JSON 数值或超出 BIGINT 返回 400；未声明的 Query 返回 400；字段为空、格式或长度非法返回 422 字段错误；非 JSON 媒体类型返回 415。</li>
 *
 * @see DecisionReleaseManagementApi#createCandidate(String, String, CreateTestReleaseRequestBody)
 */
class TestReleaseCreateApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-releases/v1/candidates";

    private static final long ACTOR = CandidateBuilder.ACTOR_ID;

    @Autowired
    private CandidateMocker candidates;

    @Autowired
    private ReleaseStoreChecker store;

    /** 以冻结版本创建 Candidate：编号为现有最大值加一，响应与持久化根及初始事件一致。 */
    @Test
    void test_normalFlow() {
        MockedCandidate existing = candidates.mockCandidate(CandidateBuilder.validationPassed());
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        Instant before = now();
        var response = create(request(composition));

        response.assertCreated(store, composition, existing.releaseNo() + 1, ACTOR, before, now());
        store.assertUnchanged(existing);
    }

    /** 同一版本重复调用各自新建 Candidate，编号递增，发布 ID 与内容摘要不同。 */
    @Test
    void test_normalFlow_eachCallCreatesNewCandidate() {
        MockedCandidate existing = candidates.mockCandidate(CandidateBuilder.draft());
        CompositionFacts composition = existing.composition();

        Instant before = now();
        var first = create(request(composition));
        long firstId = first.assertCreated(store, composition, existing.releaseNo() + 1, ACTOR, before, now());
        var second = create(request(composition));
        long secondId = second.assertCreated(store, composition, existing.releaseNo() + 2, ACTOR, before, now());

        assertNotEquals(first.contentHash(), second.contentHash());
        store.assertReleases(composition, existing.releaseId(), firstId, secondId);
        store.assertUnchanged(existing);
    }

    /** 规则集已有更新的草稿版本时，历史冻结版本仍可创建。 */
    @Test
    void test_normalFlow_historicalFrozenVersion() {
        MockedCandidate existing = candidates.mockCandidate(CandidateBuilder.draft());
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition().newerDraftVersion());

        Instant before = now();
        create(request(composition)).assertCreated(store, composition, existing.releaseNo() + 1, ACTOR, before,
            now());
    }

    /** 当前最大编号为 2147483646 时新编号恰为整数上限。 */
    @Test
    void test_edgeCase_releaseNoReachesMaximum() {
        candidates.mockCandidate(CandidateBuilder.draft().releaseNo("2147483646"));
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        Instant before = now();
        create(request(composition)).assertCreated(store, composition, Integer.MAX_VALUE, ACTOR, before, now());
    }

    /** 规则集版本未登记。 */
    @Test
    void test_noDataFound() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(new CreateTestReleaseRequestBody("999999999999999999", composition.ruleSetHash()))
            .assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
        store.assertReleases(composition);
    }

    /** 调用方读取的内容摘要与当前版本不符。 */
    @Test
    void test_businessRuleViolation_contentHashChanged() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(new CreateTestReleaseRequestBody(Long.toString(composition.ruleSetVersionId()), "f".repeat(64)))
            .assertProblem(409, CONCURRENT_REQUEST.getCode());
        store.assertReleases(composition);
    }

    /** 规则集已整体删除，编码不能再创建 Candidate。 */
    @Test
    void test_businessRuleViolation_ruleSetDeleted() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition().deletedRoot());

        create(request(composition)).assertProblem(409, RISK_RULE_SET_DELETED.getCode());
        store.assertReleases(composition);
    }

    /** 草稿规则版本不具备创建资格。 */
    @Test
    void test_businessRuleViolation_draftVersion() {
        CompositionFacts composition = candidates.mockComposition(
            CandidateBuilder.composition().ruleSetStatus(RuleSetVersionStatus.DRAFT));

        create(request(composition)).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        store.assertReleases(composition);
    }

    /** 没有旧发布组成的规则版本只支持配置，不能创建 Candidate。 */
    @Test
    void test_businessRuleViolation_withoutCompanion() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition().withoutCompanion());
        MockedCandidate other = candidates.mockCandidate(CandidateBuilder.draft());

        create(request(composition)).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        store.assertReleases(other.composition(), other.releaseId());
        store.assertUnchanged(other);
    }

    /** 统一变量格式的规则版本仅支持配置，不能创建执行候选。 */
    @Test
    void test_businessRuleViolation_variableConfiguration() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());
        candidates.markVariableConfiguration(composition);

        create(request(composition)).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        store.assertReleases(composition);
    }

    /** 存储的规则内容摘要与规范重算不符时失败关闭，即使与调用方预期一致。 */
    @Test
    void test_businessRuleViolation_storedRuleSetHashDriftFailsClosed() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());
        String drifted = "a".repeat(64);
        candidates.corruptRuleSetHash(composition, drifted);

        create(new CreateTestReleaseRequestBody(Long.toString(composition.ruleSetVersionId()), drifted))
            .assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertReleases(composition);
    }

    /** 决策点版本内容摘要漂移时失败关闭。 */
    @Test
    void test_businessRuleViolation_pointVersionHashDriftFailsClosed() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());
        candidates.corruptPointVersionHash(composition);

        create(request(composition)).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertReleases(composition);
    }

    /** 全表任一发布编号不是规范正整数时拒绝分配新编号。 */
    @Test
    void test_businessRuleViolation_nonCanonicalReleaseNoFailsClosed() {
        MockedCandidate legacy = candidates.mockCandidate(CandidateBuilder.draft().releaseNo("0123"));
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(request(composition)).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertReleases(composition);
        store.assertReleases(legacy.composition(), legacy.releaseId());
    }

    /** 发布编号已达整数上限时不能继续分配。 */
    @Test
    void test_businessRuleViolation_releaseNoExhaustedFailsClosed() {
        candidates.mockCandidate(CandidateBuilder.draft().releaseNo(Integer.toString(Integer.MAX_VALUE)));
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(request(composition)).assertProblem(500, INTERNAL_ERROR.getCode());
        store.assertReleases(composition);
    }

    /** Body 缺少必填键、不是单个 JSON 对象或 ID 超出 BIGINT，在业务处理前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableBodies")
    void test_invalidParameter(String scenario, String rawBody) {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(fill(rawBody, composition)).assertProblem(400, INVALID_REQUEST.getCode());
        store.assertReleases(composition);
    }

    /** 写接口不声明 Query，携带任何查询参数均在业务处理前拒绝。 */
    @Test
    void test_invalidParameter_query() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());
        String requestId = nextRequestId();

        new TestReleaseCreateResponseChecker(http.post(PATH + "?environmentCode=TEST", headers(requestId),
            request(composition)), objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
        store.assertReleases(composition);
    }

    /** 字段为空或格式、长度非法时返回字段级校验错误。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFields")
    void test_invalidParameter_fieldValidation(String scenario, String field, String rawBody) {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());

        create(fill(rawBody, composition)).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);
        store.assertReleases(composition);
    }

    /** 非 JSON 媒体类型在进入合同前拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        CompositionFacts composition = candidates.mockComposition(CandidateBuilder.composition());
        String requestId = nextRequestId();
        HttpHeaders headers = headers(requestId);
        headers.setContentType(MediaType.TEXT_PLAIN);

        new TestReleaseCreateResponseChecker(http.post(PATH, headers, json(request(composition))), objectMapper,
            requestId).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());
        store.assertReleases(composition);
    }

    static Stream<Arguments> unreadableBodies() {
        return Stream.of(Arguments.of("空对象", "{}"),
            Arguments.of("缺少 ruleSetVersionId", "{\"expectedRuleSetContentHash\":\"{hash}\"}"),
            Arguments.of("缺少 expectedRuleSetContentHash", "{\"ruleSetVersionId\":\"{id}\"}"),
            Arguments.of("ruleSetVersionId 超过 BIGINT", body("9223372036854775808", "{hash}")),
            Arguments.of("ruleSetVersionId 为 JSON 数值", "{\"ruleSetVersionId\":{id},\"expectedRuleSetContentHash\":\"{hash}\"}"),
            Arguments.of("expectedRuleSetContentHash 为 JSON 数值", "{\"ruleSetVersionId\":\"{id}\",\"expectedRuleSetContentHash\":1}"),
            Arguments.of("ruleSetVersionId 重复键", "{\"ruleSetVersionId\":\"1\",\"ruleSetVersionId\":\"{id}\","
                + "\"expectedRuleSetContentHash\":\"{hash}\"}"),
            Arguments.of("含未知字段", "{\"ruleSetVersionId\":\"{id}\",\"expectedRuleSetContentHash\":\"{hash}\","
                + "\"environmentCode\":\"TEST\"}"),
            Arguments.of("非 JSON", "ruleSetVersionId={id}"), Arguments.of("JSON 数组", "[]"),
            Arguments.of("截断 JSON", "{\"ruleSetVersionId\":\"{id}\""));
    }

    static Stream<Arguments> invalidFields() {
        String id = "ruleSetVersionId";
        String hash = "expectedRuleSetContentHash";
        return Stream.of(Arguments.of("ruleSetVersionId 为 null", id, "{\"ruleSetVersionId\":null,"
                + "\"expectedRuleSetContentHash\":\"{hash}\"}"),
            Arguments.of("ruleSetVersionId 为 0", id, body("0", "{hash}")),
            Arguments.of("ruleSetVersionId 前导零", id, body("0{id}", "{hash}")),
            Arguments.of("ruleSetVersionId 为负数", id, body("-1", "{hash}")),
            Arguments.of("ruleSetVersionId 超过 19 位", id, body("1".repeat(20), "{hash}")),
            Arguments.of("ruleSetVersionId 非数字", id, body("abc", "{hash}")),
            Arguments.of("expectedRuleSetContentHash 为 null", hash, "{\"ruleSetVersionId\":\"{id}\","
                + "\"expectedRuleSetContentHash\":null}"),
            Arguments.of("expectedRuleSetContentHash 大写", hash, body("{id}", "A".repeat(64))),
            Arguments.of("expectedRuleSetContentHash 63 位", hash, body("{id}", "a".repeat(63))),
            Arguments.of("expectedRuleSetContentHash 65 位", hash, body("{id}", "a".repeat(65))),
            Arguments.of("expectedRuleSetContentHash 非十六进制", hash, body("{id}", "g".repeat(64))));
    }

    /** 两个字段均为字符串的原始 Body。 */
    private static String body(String ruleSetVersionId, String hash) {
        return "{\"ruleSetVersionId\":\"" + ruleSetVersionId + "\",\"expectedRuleSetContentHash\":\"" + hash + "\"}";
    }

    /** 以组成的真实版本 ID 与摘要替换占位符。 */
    private static String fill(String rawBody, CompositionFacts composition) {
        return rawBody.replace("{id}", Long.toString(composition.ruleSetVersionId()))
            .replace("{hash}", composition.ruleSetHash());
    }

    private static CreateTestReleaseRequestBody request(CompositionFacts composition) {
        return new CreateTestReleaseRequestBody(Long.toString(composition.ruleSetVersionId()),
            composition.ruleSetHash());
    }

    private TestReleaseCreateResponseChecker create(Object body) {
        String requestId = nextRequestId();
        return new TestReleaseCreateResponseChecker(http.post(PATH, headers(requestId), body), objectMapper,
            requestId);
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-release-create-" + UUID.randomUUID();
    }
}
