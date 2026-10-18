package com.paytech.compliance.auto.ext.http;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.compliance.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.compliance.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_EVIDENCE_REVIEWED;
import static com.paytech.compliance.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.ACCEPTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.RECEIVED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.REJECTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.SUBMITTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.individual;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.organization;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.IDENTITY_DOCUMENT;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.REGISTRATION_CERTIFICATE;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.checker.ClientReviewCommandResponseChecker;
import com.paytech.compliance.test.checker.ClientReviewStoreChecker;
import com.paytech.compliance.test.mocker.ClientReviewMocker;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedEvidence;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;

/**
 * O05 核验一份材料
 * <li>正常流程：主任务或子任务的证据接受/拒绝，证据与所属要求状态及版本改变，主任务版本递增，状态不变。</li>
 * <li>要求状态由其全部证据推导：任一 ACCEPTED 为 ACCEPTED，否则仍有 RECEIVED 为 RECEIVED，其余 REJECTED；未改变时不更新要求。</li>
 * <li>重放：证据已是同一结果直接返回，任务已结束也一样，不写任何行。</li>
 * <li>未找到：任务不存在、是子任务，或证据不属于该任务一律 404。</li>
 * <li>参数：结果缺失或不在 ACCEPTED/REJECTED 内 422；路径 ID 非法、报文无法解析 400；非 JSON 415。</li>
 * <li>业务规则：任务已结束 409 CLIENT_REVIEW_CLOSED；已核验为其他结果 409 CLIENT_REVIEW_EVIDENCE_REVIEWED。</li>
 *
 * @see ClientReviewAdminApi#reviewEvidence(String, String, String, String, ClientReviewEvidenceReviewRequestBody)
 */
class ClientReviewEvidenceReviewApiAutoTest extends ComplianceMockTestBase {
    private static final String PATH = "/client-onboarding-admin/v1/cases/%s/evidence/%s/review";

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private ClientReviewStoreChecker store;

    /** 接受主任务的唯一证据：证据与要求都变为 ACCEPTED。 */
    @Test
    void test_normalFlow_acceptMainEvidence() {
        MockedReview review = reviews.mock(organization());
        MockedEvidence evidence = review.evidence(0, REGISTRATION_CERTIFICATE);

        review(review, evidence, ACCEPTED).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().evidence(evidence, ACCEPTED)
            .requirement(review.requirement(0, REGISTRATION_CERTIFICATE), ACCEPTED));
    }

    /** 拒绝子任务的唯一证据：要求变为 REJECTED，仍以主任务坐标返回。 */
    @Test
    void test_normalFlow_rejectChildEvidence() {
        MockedReview review = reviews.mock(organization());
        MockedEvidence evidence = review.evidence(2, IDENTITY_DOCUMENT);

        review(review, evidence, REJECTED).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().evidence(evidence, REJECTED)
            .requirement(review.requirement(2, IDENTITY_DOCUMENT), REJECTED));
    }

    /** 同一要求两份待核验证据：拒绝一份时要求仍为 RECEIVED 且不更新，再接受另一份时变为 ACCEPTED。 */
    @Test
    void test_normalFlow_requirementDerivedFromAllEvidence() {
        MockedReview review = reviews.mock(organization().childRequirement(0, IDENTITY_DOCUMENT, RECEIVED, RECEIVED, RECEIVED));
        var requirement = review.requirement(1, IDENTITY_DOCUMENT);
        MockedEvidence first = requirement.evidence().get(0);
        MockedEvidence second = requirement.evidence().get(1);

        review(review, first, REJECTED).assertAccepted(review.caseId(), SUBMITTED);
        store.assertStore(store.expect(review).mainTouched().evidence(first, REJECTED));

        review(review, second, ACCEPTED).assertAccepted(review.caseId(), SUBMITTED);
        store.assertStore(store.expect(review).mainTouched().mainTouched().evidence(first, REJECTED)
            .evidence(second, ACCEPTED).requirement(requirement, ACCEPTED));
    }

    /** 要求已有被接受的证据时，拒绝另一份不改变要求。 */
    @Test
    void test_edgeCase_acceptedRequirementKept() {
        MockedReview review = reviews.mock(individual().mainRequirement(IDENTITY_DOCUMENT, ACCEPTED, ACCEPTED, RECEIVED));
        MockedEvidence pending = review.requirement(0, IDENTITY_DOCUMENT).evidence().get(1);

        review(review, pending, REJECTED).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().evidence(pending, REJECTED));
    }

    /** 最后一份待核验证据被拒绝且没有被接受的证据时，要求变为 REJECTED。 */
    @Test
    void test_edgeCase_lastPendingRejected() {
        MockedReview review = reviews.mock(individual().mainRequirement(IDENTITY_DOCUMENT, RECEIVED, REJECTED, RECEIVED));
        var requirement = review.requirement(0, IDENTITY_DOCUMENT);
        MockedEvidence pending = requirement.evidence().get(1);

        review(review, pending, REJECTED).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().evidence(pending, REJECTED).requirement(requirement, REJECTED));
    }

    /** 证据已是同一结果：直接返回当前结果，不写任何行。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ACCEPTED, REJECTED})
    void test_edgeCase_replay(String outcome) {
        MockedReview review = reviews.mock(organization().childRequirement(0, IDENTITY_DOCUMENT, outcome, outcome));

        review(review, review.evidence(1, IDENTITY_DOCUMENT), outcome).assertAccepted(review.caseId(), SUBMITTED);

        store.assertUnchanged(review);
    }

    /** 任务已结束但证据已是同一结果时仍按重放返回结束后的状态。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_edgeCase_replayAfterClosed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().readyForApproval().closed(action));

        review(review, review.evidence(0, REGISTRATION_CERTIFICATE), ACCEPTED).assertAccepted(review.caseId(),
            action.getCaseStatus());

        store.assertUnchanged(review);
    }

    /** 任务不存在、是子任务，或证据不存在、属于其他任务。 */
    @Test
    void test_noDataFound() {
        MockedReview review = reviews.mock(organization());
        MockedReview other = reviews.mock(organization());
        MockedEvidence own = review.evidence(1, IDENTITY_DOCUMENT);

        invoke(PATH.formatted("999999999999999", own.evidenceId()), body(ACCEPTED))
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        invoke(PATH.formatted(review.child(0).caseId(), own.evidenceId()), body(ACCEPTED))
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        invoke(PATH.formatted(review.caseId(), "999999999999999"), body(ACCEPTED))
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        review(review, other.evidence(0, REGISTRATION_CERTIFICATE), ACCEPTED).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);

        store.assertUnchanged(review);
        store.assertUnchanged(other);
    }

    /** 不属于 Client 入驻的任务按不存在处理。 */
    @Test
    void test_noDataFound_otherNamespace() {
        MockedReview partner = reviews.mock(individual().sourceNamespace("CCT_PARTNER_APPLICATION"));

        review(partner, partner.evidence(0, IDENTITY_DOCUMENT), ACCEPTED).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);

        store.assertUnchanged(partner);
    }

    /** 核验结果缺失或不在允许值内。 */
    @ParameterizedTest(name = "outcome={0}")
    @MethodSource("invalidOutcomes")
    void test_invalidParameter(String outcome) {
        MockedReview review = reviews.mock(organization());

        review(review, review.evidence(0, REGISTRATION_CERTIFICATE), outcome).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError("outcome");

        store.assertUnchanged(review);
    }

    /** 路径中的任务或证据 ID 格式非法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPaths")
    void test_invalidParameter_path(String scenario, String caseId, String evidenceId) {
        MockedReview review = reviews.mock(organization());
        String resolvedCase = caseId == null ? Long.toString(review.caseId()) : caseId;
        String resolvedEvidence = evidenceId == null ? Long.toString(review.evidence(0, REGISTRATION_CERTIFICATE).evidenceId())
            : evidenceId;

        invoke(PATH.formatted(resolvedCase, resolvedEvidence), body(ACCEPTED)).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(review);
    }

    /** 无法解析、带未知字段的报文与非 JSON 媒体类型。 */
    @Test
    void test_invalidParameter_malformedBody() {
        MockedReview review = reviews.mock(organization());
        String path = PATH.formatted(review.caseId(), review.evidence(0, REGISTRATION_CERTIFICATE).evidenceId());

        invoke(path, headers(), "{\"outcome\":").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, headers(), "").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, headers(), "{\"outcome\":\"ACCEPTED\",\"reviewer\":\"70001\"}").assertProblem(400,
            INVALID_REQUEST.getCode());
        HttpHeaders text = headers();
        text.setContentType(MediaType.TEXT_PLAIN);
        invoke(path, text, "{\"outcome\":\"ACCEPTED\"}").assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertUnchanged(review);
    }

    /** 任务已结束：待核验证据不能再核验。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_businessRuleViolation_closed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().closed(action));

        review(review, review.evidence(0, REGISTRATION_CERTIFICATE), ACCEPTED).assertReviewError(409, CLIENT_REVIEW_CLOSED);

        store.assertUnchanged(review);
    }

    /** 证据已核验为另一结果，不能改判。 */
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("reviewedEvidence")
    void test_businessRuleViolation_alreadyReviewed(String current, String requested) {
        MockedReview review = reviews.mock(organization().childRequirement(1, IDENTITY_DOCUMENT, current, current));

        review(review, review.evidence(2, IDENTITY_DOCUMENT), requested)
            .assertReviewError(409, CLIENT_REVIEW_EVIDENCE_REVIEWED);

        store.assertUnchanged(review);
    }

    static Stream<String> invalidOutcomes() {
        return Stream.of(null, "", "accepted", "RECEIVED", "PENDING");
    }

    static Stream<Arguments> invalidPaths() {
        return Stream.of(Arguments.of("任务 ID 为 0", "0", null), Arguments.of("任务 ID 非数字", "abc", null),
            Arguments.of("任务 ID 20 位", "1".repeat(20), null), Arguments.of("证据 ID 前导零", null, "012"),
            Arguments.of("证据 ID 负数", null, "-1"), Arguments.of("证据 ID 超出 BIGINT", null, "9223372036854775808"));
    }

    static Stream<Arguments> reviewedEvidence() {
        return Stream.of(Arguments.of(ACCEPTED, REJECTED), Arguments.of(REJECTED, ACCEPTED));
    }

    private ClientReviewCommandResponseChecker review(MockedReview review, MockedEvidence evidence, String outcome) {
        return invoke(PATH.formatted(review.caseId(), evidence.evidenceId()), body(outcome));
    }

    /** 核验请求体；null 表示缺少 outcome。 */
    private static Object body(String outcome) {
        var body = new ClientReviewEvidenceReviewRequestBody();
        body.setOutcome(outcome);
        return outcome == null ? Map.of() : body;
    }

    private ClientReviewCommandResponseChecker invoke(String path, Object body) {
        return invoke(path, headers(), body);
    }

    private ClientReviewCommandResponseChecker invoke(String path, HttpHeaders headers, Object body) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new ClientReviewCommandResponseChecker(http.post(path, headers, body), objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-review-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}
