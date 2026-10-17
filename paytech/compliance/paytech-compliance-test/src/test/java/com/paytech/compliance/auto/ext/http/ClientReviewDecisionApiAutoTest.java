package com.paytech.risk.auto.ext.http.compliance;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_READY;
import static com.paytech.risk.compliance.domain.ClientReviewAction.APPROVE;
import static com.paytech.risk.compliance.domain.ClientReviewAction.REJECT;
import static com.paytech.risk.compliance.domain.ClientReviewAction.RETURN;
import static com.paytech.risk.compliance.domainservice.policy.ClientReviewApprovalPolicy.CHILD_CASE_NOT_ACTIVE;
import static com.paytech.risk.compliance.domainservice.policy.ClientReviewApprovalPolicy.CLASSIFICATION_NOT_RECEIVED;
import static com.paytech.risk.compliance.domainservice.policy.ClientReviewApprovalPolicy.EVIDENCE_PENDING;
import static com.paytech.risk.compliance.domainservice.policy.ClientReviewApprovalPolicy.REQUIRED_MATERIAL_NOT_ACCEPTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.ACCEPTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.RECEIVED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.REJECTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.SUBMITTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.individual;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.organization;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.IDENTITY_DOCUMENT;
import static com.paytech.risk.test.builder.compliance.ClientReviewRegistrationRequestBuilder.PROOF_OF_ADDRESS;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.compliance.ClientReviewAdminApi;
import com.paytech.risk.api.compliance.request.ClientReviewDecisionRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewReturnRequestBody;
import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder;
import com.paytech.risk.test.builder.compliance.ClientReviewClassificationsRequestBuilder;
import com.paytech.risk.test.builder.compliance.ClientReviewDecisionRequestBuilder;
import com.paytech.risk.test.builder.compliance.ClientReviewReturnRequestBuilder;
import com.paytech.risk.test.checker.compliance.ClientReviewCommandResponseChecker;
import com.paytech.risk.test.checker.compliance.ClientReviewStoreChecker;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.CaseRow;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedEvidence;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedReview;
import com.paytech.risk.test.probe.ClientReviewResultProbe;

/**
 * O07 退回修改、O08 通过、O09 最终拒绝
 * <li>通过：门禁满足后主任务与全部进行中子任务结束为 APPROVED，每个任务一条 REVIEWER 资格决定与状态变更。</li>
 * <li>拒绝：主任务 REJECTED 并有一条决定，进行中子任务随之 WITHDRAWN；内部理由只进内部备注，不复制为客户可见说明。</li>
 * <li>退回：主任务与进行中子任务 RETURNED，记录分区与客户可见说明，不形成决定。</li>
 * <li>每个结论在同一事务登记结果发布，经补偿任务发布 ClientReviewDecided 到 topic_risk_client_review；
 * 用例绑定临时队列观察消息信封、payloadHash 与 payload。</li>
 * <li>重放：已以同一结论结束直接返回，不再写行或发布；端到端：从核验材料、接收分类到通过的完整审核。</li>
 * <li>业务规则：门禁未满足 422（逐项给出未满足条件）；已以其他结论结束 409；任务不存在、是子任务或不属于 Client 入驻 404。</li>
 * <li>参数：请求字段格式 422；路径 ID 与报文 400；非 JSON 415。被拒绝的命令不写任何行也不发布。</li>
 *
 * @see ClientReviewAdminApi#returnCase(String, String, String, ClientReviewReturnRequestBody)
 * @see ClientReviewAdminApi#approveCase(String, String, String, ClientReviewDecisionRequestBody)
 * @see ClientReviewAdminApi#rejectCase(String, String, String, ClientReviewDecisionRequestBody)
 */
class ClientReviewDecisionApiAutoTest extends MockTestBase {
    private static final String CASES = "/client-onboarding-admin/v1/cases/";

    private static final String CLASSIFY = "/client-onboarding-review/v1/cases/%s/classifications";

    private static final long ACTOR = 930000001L;

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private ClientReviewStoreChecker store;

    @Autowired
    private ClientReviewResultProbe results;

    /** 每例一个临时结果队列。 */
    @BeforeEach
    void setUpClassMocks() {
        results.bind();
    }

    /** 删除临时结果队列。 */
    @AfterEach
    void tearDownClassMocks() {
        results.unbind();
    }

    /** 门禁满足的组织任务通过：主任务与两个子任务 APPROVED，三条决定，结果发布。 */
    @Test
    void test_normalFlow_approve() {
        MockedReview review = reviews.mock(organization().readyForApproval());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();

        approve(review, request).assertAccepted(review.caseId(), APPROVE.getCaseStatus());

        store.assertStore(store.expect(review).decided(APPROVE, ACTOR, request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, started));
        assertPublished(review, APPROVE, ACTOR, request.getPublicNote(), null);
    }

    /** 个人任务通过且不带客户可见说明：只有主任务一条决定，说明为空。 */
    @Test
    void test_normalFlow_approveIndividualWithoutPublicNote() {
        MockedReview review = reviews.mock(individual().readyForApproval());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().publicNote(null).build();
        Instant started = Instant.now();

        approve(review, request).assertAccepted(review.caseId(), APPROVE.getCaseStatus());

        store.assertStore(store.expect(review).decided(APPROVE, ACTOR, request.getReasonCode(), request.getReason(), null,
            null, started));
        assertPublished(review, APPROVE, ACTOR, null, null);
    }

    /** 最终拒绝不要求门禁：主任务 REJECTED，子任务 WITHDRAWN，内部理由不进入客户可见说明。 */
    @Test
    void test_normalFlow_reject() {
        MockedReview review = reviews.mock(organization());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().publicNote(null).build();
        Instant started = Instant.now();

        reject(review, request).assertAccepted(review.caseId(), REJECT.getCaseStatus());

        store.assertStore(store.expect(review).decided(REJECT, ACTOR, request.getReasonCode(), request.getReason(), null,
            null, started));
        assertPublished(review, REJECT, ACTOR, null, null);
    }

    /** 拒绝时附带客户可见说明，随结果发布。 */
    @Test
    void test_normalFlow_rejectWithPublicNote() {
        MockedReview review = reviews.mock(individual());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();

        reject(review, request).assertAccepted(review.caseId(), REJECT.getCaseStatus());

        store.assertStore(store.expect(review).decided(REJECT, ACTOR, request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, started));
        assertPublished(review, REJECT, ACTOR, request.getPublicNote(), null);
    }

    /** 退回修改：主任务与子任务 RETURNED，不形成决定，分区与说明随结果发布，决定字段为空。 */
    @Test
    void test_normalFlow_return() {
        MockedReview review = reviews.mock(organization());
        ClientReviewReturnRequestBody request = ClientReviewReturnRequestBuilder.template().build();
        Instant started = Instant.now();

        returnCase(review, request).assertAccepted(review.caseId(), RETURN.getCaseStatus());

        store.assertStore(store.expect(review).decided(RETURN, ACTOR, request.getReasonCode(), null,
            request.getPublicNote(), request.getSection(), started));
        assertPublished(review, RETURN, null, request.getPublicNote(), request.getSection());
    }

    /** 已通过的任务再次通过返回当前结果且不再发布；其他结论 409，事实保持通过后的样子。 */
    @Test
    void test_normalFlow_replayAfterDecision() {
        MockedReview review = reviews.mock(organization().readyForApproval());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();
        approve(review, request).assertAccepted(review.caseId(), APPROVE.getCaseStatus());
        assertPublished(review, APPROVE, ACTOR, request.getPublicNote(), null);

        approve(review, ClientReviewDecisionRequestBuilder.template().reason("Another reason").build())
            .assertAccepted(review.caseId(), APPROVE.getCaseStatus());
        reject(review, request).assertReviewError(409, CLIENT_REVIEW_CLOSED);
        returnCase(review, ClientReviewReturnRequestBuilder.template().build()).assertReviewError(409, CLIENT_REVIEW_CLOSED);

        store.assertStore(store.expect(review).decided(APPROVE, ACTOR, request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, started));
        results.assertNoResult(review.caseId());
    }

    /** 端到端：门禁未满足 422，逐份接受材料、接收分类后通过，结果发布。 */
    @Test
    void test_normalFlow_reviewToApproval() {
        MockedReview review = reviews.mock(organization());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        approve(review, request).assertReviewError(422, CLIENT_REVIEW_NOT_READY)
            .assertDetailContains(EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED, CLASSIFICATION_NOT_RECEIVED);

        for (MockedEvidence evidence : review.allEvidence()) {
            var body = new ClientReviewEvidenceReviewRequestBody();
            body.setOutcome(ACCEPTED);
            invoke(CASES + review.caseId() + "/evidence/" + evidence.evidenceId() + "/review", operations(), body)
                .assertAccepted(review.caseId(), SUBMITTED);
        }
        HttpHeaders cct = TestRequestHeaders.internal(requestId());
        cct.set("X-Caller-Service", "client-contract-service");
        invoke(CLASSIFY.formatted(review.caseId()), cct,
            ClientReviewClassificationsRequestBuilder.template(review.applicationId(), review.submissionId()).build())
            .assertAccepted(review.caseId(), SUBMITTED);
        approve(review, request).assertAccepted(review.caseId(), APPROVE.getCaseStatus());

        CaseRow main = store.caseRow(review.caseId());
        // 三次核验与一次分类各使主任务版本递增，结束再递增一次。
        assertPublished(review, APPROVE, main.version(), ACTOR, request.getPublicNote(), null);
        org.junit.jupiter.api.Assertions.assertEquals(6, main.version(), "主任务版本");
    }

    /** 子任务已单独结束时，拒绝与退回只结束仍在进行的任务，已结束的子任务保持原样。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("childClosedActions")
    void test_edgeCase_childAlreadyClosed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().childClosed(1, REJECTED));
        Instant started = Instant.now();
        if (action == RETURN) {
            ClientReviewReturnRequestBody request = ClientReviewReturnRequestBuilder.template().build();
            returnCase(review, request).assertAccepted(review.caseId(), RETURN.getCaseStatus());
            store.assertStore(store.expect(review).decided(RETURN, ACTOR, request.getReasonCode(), null,
                request.getPublicNote(), request.getSection(), started));
            assertPublished(review, RETURN, null, request.getPublicNote(), request.getSection());
            return;
        }
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        reject(review, request).assertAccepted(review.caseId(), REJECT.getCaseStatus());
        store.assertStore(store.expect(review).decided(REJECT, ACTOR, request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, started));
        assertPublished(review, REJECT, ACTOR, request.getPublicNote(), null);
    }

    /** 已以同一结论结束的任务：直接返回，不写行也不发布。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_edgeCase_replayClosed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().readyForApproval().closed(action));

        decide(review, action).assertAccepted(review.caseId(), action.getCaseStatus());

        store.assertUnchanged(review);
        results.assertNoResult(review.caseId());
    }

    /** 门禁未满足：422 并给出全部未满足条件，不写行也不发布。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("notReady")
    void test_businessRuleViolation_notReady(String scenario, Supplier<ClientReviewCaseBuilder> fixture, List<String> blockers) {
        MockedReview review = reviews.mock(fixture.get());

        approve(review, ClientReviewDecisionRequestBuilder.template().build()).assertReviewError(422, CLIENT_REVIEW_NOT_READY)
            .assertDetailContains(blockers.toArray(String[]::new));

        store.assertUnchanged(review);
        results.assertNoResult(review.caseId());
    }

    /** 已以其他结论结束：409，不写行也不发布。 */
    @ParameterizedTest(name = "{0} 后 {1}")
    @MethodSource("otherOutcomes")
    void test_businessRuleViolation_closedWithOtherOutcome(ClientReviewAction closedBy, ClientReviewAction requested) {
        MockedReview review = reviews.mock(organization().readyForApproval().closed(closedBy));

        decide(review, requested).assertReviewError(409, CLIENT_REVIEW_CLOSED);

        store.assertUnchanged(review);
        results.assertNoResult(review.caseId());
    }

    /** 任务不存在、是子任务或不属于 Client 入驻：三种结论一律 404。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_noDataFound(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().readyForApproval());
        MockedReview partner = reviews.mock(individual().readyForApproval().sourceNamespace("CCT_PARTNER_APPLICATION"));

        decide("999999999999999", action).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        decide(Long.toString(review.child(0).caseId()), action).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        decide(Long.toString(partner.caseId()), action).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);

        store.assertUnchanged(review);
        store.assertUnchanged(partner);
    }

    /** 通过与拒绝请求字段格式非法：422 并定位字段。 */
    @ParameterizedTest(name = "{0} {1}: {2}")
    @MethodSource("invalidDecisionFields")
    void test_invalidParameter_decision(ClientReviewAction action, String field, String scenario,
        Supplier<ClientReviewDecisionRequestBuilder> request) {
        MockedReview review = reviews.mock(organization().readyForApproval());

        invoke(CASES + review.caseId() + "/" + path(action), operations(), request.get().build())
            .assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        store.assertUnchanged(review);
    }

    /** 退回请求字段格式非法：422 并定位字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidReturnFields")
    void test_invalidParameter_return(String field, String scenario, Supplier<ClientReviewReturnRequestBuilder> request) {
        MockedReview review = reviews.mock(organization());

        returnCase(review, request.get().build()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        store.assertUnchanged(review);
    }

    /** 路径中的任务 ID 格式非法。 */
    @ParameterizedTest(name = "{0} caseId={1}")
    @MethodSource("invalidCaseIds")
    void test_invalidParameter_caseId(ClientReviewAction action, String caseId) {
        decide(caseId, action).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 无法解析、带未知字段的报文与非 JSON 媒体类型，三种结论一致拒绝。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_invalidParameter_malformedBody(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().readyForApproval());
        String path = CASES + review.caseId() + "/" + path(action);
        String valid = objectMapper.writeValueAsString(body(action));

        invoke(path, operations(), "{\"reasonCode\":").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, operations(), "").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, operations(), valid.replaceFirst("\\{", "{\"outcome\":\"APPROVED\",")).assertProblem(400,
            INVALID_REQUEST.getCode());
        HttpHeaders text = operations();
        text.setContentType(MediaType.TEXT_PLAIN);
        invoke(path, text, valid).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertUnchanged(review);
        results.assertNoResult(review.caseId());
    }

    static Stream<ClientReviewAction> childClosedActions() {
        return Stream.of(REJECT, RETURN);
    }

    /** 每例只让一项门禁不满足（首例为刚登记的任务）。 */
    static Stream<Arguments> notReady() {
        return Stream.of(
            Arguments.of("刚登记", fixture(ClientReviewCaseBuilder::organization),
                List.of(EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED, CLASSIFICATION_NOT_RECEIVED)),
            Arguments.of("未接收分类", fixture(() -> organization().readyForApproval().withoutClassifications()),
                List.of(CLASSIFICATION_NOT_RECEIVED)),
            Arguments.of("非必需材料待核验",
                fixture(() -> organization().readyForApproval().childRequirement(0, PROOF_OF_ADDRESS, RECEIVED, RECEIVED)),
                List.of(EVIDENCE_PENDING)),
            Arguments.of("必需材料被拒绝",
                fixture(() -> organization().readyForApproval().childRequirement(1, IDENTITY_DOCUMENT, REJECTED, REJECTED)),
                List.of(REQUIRED_MATERIAL_NOT_ACCEPTED)),
            Arguments.of("必需材料没有证据",
                fixture(() -> individual().readyForApproval().mainRequirement(IDENTITY_DOCUMENT, ClientReviewCaseBuilder.PENDING)),
                List.of(REQUIRED_MATERIAL_NOT_ACCEPTED)),
            Arguments.of("子任务已单独结束", fixture(() -> organization().readyForApproval().childClosed(0, APPROVE.getChildStatus())),
                List.of(CHILD_CASE_NOT_ACTIVE)));
    }

    static Stream<Arguments> otherOutcomes() {
        return Stream.of(Arguments.of(APPROVE, REJECT), Arguments.of(APPROVE, RETURN), Arguments.of(REJECT, APPROVE),
            Arguments.of(REJECT, RETURN), Arguments.of(RETURN, APPROVE), Arguments.of(RETURN, REJECT));
    }

    static Stream<Arguments> invalidDecisionFields() {
        return Stream.of(APPROVE, REJECT).flatMap(action -> Stream.of(
            decision(action, "reasonCode", "缺失", () -> ClientReviewDecisionRequestBuilder.template().reasonCode(null)),
            decision(action, "reasonCode", "小写", () -> ClientReviewDecisionRequestBuilder.template().reasonCode("verified")),
            decision(action, "reasonCode", "65 位",
                () -> ClientReviewDecisionRequestBuilder.template().reasonCode("R" + "0".repeat(64))),
            decision(action, "reason", "缺失", () -> ClientReviewDecisionRequestBuilder.template().reason(null)),
            decision(action, "reason", "空白", () -> ClientReviewDecisionRequestBuilder.template().reason(" ")),
            decision(action, "reason", "超过 2000 位",
                () -> ClientReviewDecisionRequestBuilder.template().reason("r".repeat(2001))),
            decision(action, "publicNote", "超过 1000 位",
                () -> ClientReviewDecisionRequestBuilder.template().publicNote("n".repeat(1001)))));
    }

    static Stream<Arguments> invalidReturnFields() {
        return Stream.of(returning("reasonCode", "缺失", () -> ClientReviewReturnRequestBuilder.template().reasonCode(null)),
            returning("reasonCode", "以数字开头", () -> ClientReviewReturnRequestBuilder.template().reasonCode("1_UNREADABLE")),
            returning("section", "缺失", () -> ClientReviewReturnRequestBuilder.template().section(null)),
            returning("section", "未知分区", () -> ClientReviewReturnRequestBuilder.template().section("FINANCE")),
            returning("section", "小写", () -> ClientReviewReturnRequestBuilder.template().section("materials")),
            returning("publicNote", "缺失", () -> ClientReviewReturnRequestBuilder.template().publicNote(null)),
            returning("publicNote", "空白", () -> ClientReviewReturnRequestBuilder.template().publicNote(" ")),
            returning("publicNote", "超过 1000 位", () -> ClientReviewReturnRequestBuilder.template().publicNote("n".repeat(1001))));
    }

    static Stream<Arguments> invalidCaseIds() {
        return Stream.of(ClientReviewAction.values()).flatMap(action -> Stream.of("0", "abc", "012", "1".repeat(20))
            .map(caseId -> Arguments.of(action, caseId)));
    }

    private static Supplier<ClientReviewCaseBuilder> fixture(Supplier<ClientReviewCaseBuilder> fixture) {
        return fixture;
    }

    private static Arguments decision(ClientReviewAction action, String field, String scenario,
        Supplier<ClientReviewDecisionRequestBuilder> request) {
        return Arguments.of(action, field, scenario, request);
    }

    private static Arguments returning(String field, String scenario, Supplier<ClientReviewReturnRequestBuilder> request) {
        return Arguments.of(field, scenario, request);
    }

    /** 结果消息与结束后的主任务、主任务决定一致；主任务版本为结束前 +1。 */
    private void assertPublished(MockedReview review, ClientReviewAction action, Long decidedBy, String publicNote,
        String returnSection) {
        assertPublished(review, action, review.main().version() + 1, decidedBy, publicNote, returnSection);
    }

    private void assertPublished(MockedReview review, ClientReviewAction action, long caseVersion, Long decidedBy,
        String publicNote, String returnSection) {
        CaseRow main = store.caseRow(review.caseId());
        Long decisionId = action == RETURN ? null : store.decisionId(review.caseId());
        results.awaitResult(review.caseId()).assertDecided(review, action, caseVersion, main.decisionTime(), decisionId,
            decidedBy, publicNote, returnSection);
        store.assertNoResultEvent(review.caseId());
    }

    private ClientReviewCommandResponseChecker approve(MockedReview review, ClientReviewDecisionRequestBody body) {
        return invoke(CASES + review.caseId() + "/approval", operations(), body);
    }

    private ClientReviewCommandResponseChecker reject(MockedReview review, ClientReviewDecisionRequestBody body) {
        return invoke(CASES + review.caseId() + "/rejection", operations(), body);
    }

    private ClientReviewCommandResponseChecker returnCase(MockedReview review, ClientReviewReturnRequestBody body) {
        return invoke(CASES + review.caseId() + "/return", operations(), body);
    }

    /** 以合法请求体发出给定结论。 */
    private ClientReviewCommandResponseChecker decide(MockedReview review, ClientReviewAction action) {
        return decide(Long.toString(review.caseId()), action);
    }

    private ClientReviewCommandResponseChecker decide(String caseId, ClientReviewAction action) {
        return invoke(CASES + caseId + "/" + path(action), operations(), body(action));
    }

    /** 结论对应的操作路径。 */
    private static String path(ClientReviewAction action) {
        return switch (action) {
            case RETURN -> "return";
            case APPROVE -> "approval";
            case REJECT -> "rejection";
        };
    }

    /** 结论对应的合法请求体。 */
    private static Object body(ClientReviewAction action) {
        return action == RETURN ? ClientReviewReturnRequestBuilder.template().build()
            : ClientReviewDecisionRequestBuilder.template().build();
    }

    private ClientReviewCommandResponseChecker invoke(String path, HttpHeaders headers, Object body) {
        return new ClientReviewCommandResponseChecker(http.post(path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** Gateway 转发的 Operations 可信身份，操作者即决定人。 */
    private static HttpHeaders operations() {
        HttpHeaders headers = TestRequestHeaders.internal(requestId());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String requestId() {
        return "risk-review-" + UUID.randomUUID();
    }
}
