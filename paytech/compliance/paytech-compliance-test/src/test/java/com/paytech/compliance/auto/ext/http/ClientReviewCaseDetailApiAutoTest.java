package com.paytech.compliance.auto.ext.http;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy.CASE_NOT_ACTIVE;
import static com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy.CHILD_CASE_NOT_ACTIVE;
import static com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy.CLASSIFICATION_NOT_RECEIVED;
import static com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy.EVIDENCE_PENDING;
import static com.paytech.compliance.domainservice.policy.ClientReviewApprovalPolicy.REQUIRED_MATERIAL_NOT_ACCEPTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.ACCEPTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.RECEIVED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.REJECTED;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.individual;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.organization;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.IDENTITY_DOCUMENT;
import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.PROOF_OF_ADDRESS;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.checker.ClientReviewCaseResponseChecker;
import com.paytech.compliance.test.mocker.ClientReviewMocker;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;

/**
 * O02 Client 入驻审核任务详情（Risk 部分）
 * <li>正常流程：主任务与关联人员子任务的主体、依赖类型、材料要求、证据，已接收分类，以及按策略顺序给出的通过门禁未满足项。</li>
 * <li>门禁：未核验证据、必需材料未接受、未接收分类、子任务已单独结束、主任务已结束分别出现；全部满足时为空。</li>
 * <li>边界：同一要求多份证据、已结束任务的结束时间与客户可见说明。</li>
 * <li>未找到：任务不存在、是子任务或不属于 Client 入驻一律 404；路径 ID 非法 400。</li>
 *
 * @see ClientReviewAdminApi#getCase(String, String, String)
 */
class ClientReviewCaseDetailApiAutoTest extends ComplianceMockTestBase {
    private static final String PATH = "/client-onboarding-admin/v1/cases/%s";

    @Autowired
    private ClientReviewMocker reviews;

    /** 刚登记的组织任务：材料待核验、必需材料未接受、未接收分类。 */
    @Test
    void test_normalFlow() {
        MockedReview review = reviews.mock(organization());

        read(review.caseId()).assertDetail(review, EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED,
            CLASSIFICATION_NOT_RECEIVED);
    }

    /** 全部证据已接受并已接收主次分类：门禁为空，分类按接收顺序返回。 */
    @Test
    void test_normalFlow_readyForApproval() {
        MockedReview review = reviews.mock(organization().readyForApproval().classification("SOFTWARE", "5734", false));

        read(review.caseId()).assertDetail(review);
    }

    /** 个人任务没有子任务，主体只有主任务本身。 */
    @Test
    void test_normalFlow_individual() {
        MockedReview review = reviews.mock(individual());

        read(review.caseId()).assertDetail(review, EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED,
            CLASSIFICATION_NOT_RECEIVED);
    }

    /** 已结束的任务：结束时间、客户可见说明与分区随结论返回，门禁报告主任务与子任务均已结束。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_edgeCase_closed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().readyForApproval().closed(action));

        read(review.caseId()).assertDetail(review, CASE_NOT_ACTIVE, CHILD_CASE_NOT_ACTIVE);
    }

    /** 子任务已单独结束时只报告子任务未在进行。 */
    @Test
    void test_edgeCase_childClosed() {
        MockedReview review = reviews.mock(organization().readyForApproval().childClosed(1, REJECTED));

        read(review.caseId()).assertDetail(review, CHILD_CASE_NOT_ACTIVE);
    }

    /** 同一要求多份证据；必需材料被拒绝、非必需材料仍待核验时分别计入门禁。 */
    @Test
    void test_edgeCase_mixedEvidence() {
        MockedReview review = reviews.mock(organization().readyForApproval()
            .childRequirement(0, IDENTITY_DOCUMENT, ACCEPTED, REJECTED, ACCEPTED)
            .childRequirement(1, IDENTITY_DOCUMENT, REJECTED, REJECTED)
            .mainRequirement(PROOF_OF_ADDRESS, RECEIVED, RECEIVED));

        read(review.caseId()).assertDetail(review, EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED);
    }

    /** 非必需材料没有证据不影响门禁。 */
    @Test
    void test_edgeCase_optionalMaterialMissing() {
        MockedReview review = reviews.mock(individual().readyForApproval());

        read(review.caseId()).assertDetail(review);
    }

    /** 整秒提交时间同样输出固定六位小数。 */
    @Test
    void test_edgeCase_wholeSecondTime() {
        MockedReview review = reviews.mock(organization().submittedTime(Instant.parse("2026-10-13T08:00:00Z")));

        read(review.caseId()).assertDetail(review, EVIDENCE_PENDING, REQUIRED_MATERIAL_NOT_ACCEPTED,
            CLASSIFICATION_NOT_RECEIVED);
    }

    /** 详情不接受任何 Query，也不接受 GET Body。 */
    @Test
    void test_invalidParameter_queryAndBody() {
        MockedReview review = reviews.mock(organization());
        String path = PATH.formatted(review.caseId());

        invoke(path + "?status=SUBMITTED").assertProblem(400, INVALID_REQUEST.getCode());
        String requestId = "risk-review-" + UUID.randomUUID();
        new ClientReviewCaseResponseChecker(http.exchange(HttpMethod.GET, path, headers(requestId), "{}"), objectMapper,
            requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 任务不存在、是子任务或不属于 Client 入驻。 */
    @Test
    void test_noDataFound() {
        MockedReview review = reviews.mock(organization());
        MockedReview partner = reviews.mock(individual().sourceNamespace("CCT_PARTNER_APPLICATION"));

        read(999999999999999L).assertNotFound();
        read(review.child(0).caseId()).assertNotFound();
        read(partner.caseId()).assertNotFound();
    }

    /** 路径中的任务 ID 格式非法。 */
    @ParameterizedTest(name = "caseId={0}")
    @MethodSource("invalidCaseIds")
    void test_invalidParameter(String caseId) {
        invoke(PATH.formatted(caseId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<String> invalidCaseIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private ClientReviewCaseResponseChecker read(long caseId) {
        return invoke(PATH.formatted(caseId));
    }

    private ClientReviewCaseResponseChecker invoke(String path) {
        String requestId = "risk-review-" + UUID.randomUUID();
        return new ClientReviewCaseResponseChecker(http.get(path, headers(requestId)), objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}
