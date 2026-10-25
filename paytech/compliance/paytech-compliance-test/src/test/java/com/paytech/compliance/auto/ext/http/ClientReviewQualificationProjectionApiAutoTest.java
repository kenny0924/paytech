package com.paytech.compliance.auto.ext.http;

import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.individual;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.organization;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.service.processor.DecideClientReviewProcessor;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.builder.ClientReviewDecisionRequestBuilder;
import com.paytech.compliance.test.builder.RoleQualificationBuilder;
import com.paytech.compliance.test.checker.ClientReviewCommandResponseChecker;
import com.paytech.compliance.test.checker.QualificationStoreChecker;
import com.paytech.compliance.test.checker.QualificationStoreChecker.ExpectedChange;
import com.paytech.compliance.test.mocker.ClientReviewMocker;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;
import com.paytech.compliance.test.mocker.QualificationMocker;
import com.paytech.compliance.test.mocker.QualificationMocker.MockedQualification;
import com.paytech.compliance.test.probe.ClientReviewResultProbe;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 入驻审核结论同事务投影到 Client 角色资格
 * <li>通过：没有资格时新建版本 1 的 APPROVED / NOT_REQUIRED；已有资格按版本切换为 APPROVED；关联人员子任务不投影。</li>
 * <li>拒绝：只把已有的 APPROVED 资格切换为 REJECTED / COMPLETED；没有资格或资格不是 APPROVED 时不写资格行。</li>
 * <li>投影写 REVIEWER 决定（主任务决定）与 OPERATIONS_REVIEW 状态变更，触发标识为决定 ID。</li>
 *
 * @see ClientReviewAdminApi#approveCase(String, String, String, ClientReviewDecisionRequestBody)
 * @see ClientReviewAdminApi#rejectCase(String, String, String, ClientReviewDecisionRequestBody)
 */
class ClientReviewQualificationProjectionApiAutoTest extends ComplianceMockTestBase {
    private static final String CASES = "/client-onboarding-admin/v1/cases/";

    private static final long ACTOR = 930000003L;

    private static final String CLIENT = QualificationRoleType.CLIENT.getCode();

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private QualificationMocker qualifications;

    @Autowired
    private QualificationStoreChecker store;

    @Autowired
    private ClientReviewResultProbe results;

    /** 每例一个临时结果队列，使结论发布不依赖共享订阅。 */
    @BeforeEach
    void setUpClassMocks() {
        results.bind();
    }

    /** 删除临时结果队列。 */
    @AfterEach
    void tearDownClassMocks() {
        results.unbind();
    }

    /** 组织任务首次通过：新建 Client 的 ORGANIZATION_KYB 资格，关联人员不形成资格。 */
    @Test
    void test_normalFlow_approveCreatesQualification() {
        MockedReview review = reviews.mock(organization().readyForApproval());
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();

        decide(review, ClientReviewAction.APPROVE, request).assertAccepted(review.caseId(), ClientReviewAction.APPROVE.getCaseStatus());

        store.assertChanged(reviewed(review, QualificationStatus.APPROVED, request, null, 1, started));
        for (int index = 1; index < review.cases().size(); index++) {
            store.assertNoQualification("RELATED_PERSON", Long.toString(review.cases().get(index).partyId()));
        }
    }

    /** 个人任务通过而 Client 已有被撤销的同类资格：沿用原行切换为 APPROVED，版本 +1。 */
    @Test
    void test_normalFlow_approveSwitchesExistingQualification() {
        MockedReview review = reviews.mock(individual().readyForApproval());
        qualifications.mock(RoleQualificationBuilder.of(QualificationRoleType.CLIENT, Long.toString(review.clientId()),
            QualificationType.INDIVIDUAL_KYC).status(QualificationStatus.REVOKED).version(2));
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();

        decide(review, ClientReviewAction.APPROVE, request).assertAccepted(review.caseId(), ClientReviewAction.APPROVE.getCaseStatus());

        store.assertChanged(reviewed(review, QualificationStatus.APPROVED, request, QualificationStatus.REVOKED, 3, started));
    }

    /** 拒绝而 Client 已有已批准资格：切换为 REJECTED / COMPLETED。 */
    @Test
    void test_normalFlow_rejectDowngradesApprovedQualification() {
        MockedReview review = reviews.mock(organization());
        qualifications.mock(RoleQualificationBuilder.of(QualificationRoleType.CLIENT, Long.toString(review.clientId()),
            QualificationType.ORGANIZATION_KYB));
        ClientReviewDecisionRequestBody request = ClientReviewDecisionRequestBuilder.template().build();
        Instant started = Instant.now();

        decide(review, ClientReviewAction.REJECT, request).assertAccepted(review.caseId(), ClientReviewAction.REJECT.getCaseStatus());

        store.assertChanged(reviewed(review, QualificationStatus.REJECTED, request, QualificationStatus.APPROVED, 2, started));
    }

    /** 拒绝而 Client 没有资格：不新建资格。 */
    @Test
    void test_edgeCase_rejectWithoutQualification() {
        MockedReview review = reviews.mock(individual());

        decide(review, ClientReviewAction.REJECT, ClientReviewDecisionRequestBuilder.template().build())
            .assertAccepted(review.caseId(), ClientReviewAction.REJECT.getCaseStatus());

        store.assertNoQualification(CLIENT, Long.toString(review.clientId()));
    }

    /** 拒绝而 Client 的资格已被撤销：资格保持原样。 */
    @Test
    void test_edgeCase_rejectKeepsIneligibleQualification() {
        MockedReview review = reviews.mock(individual());
        MockedQualification revoked = qualifications.mock(RoleQualificationBuilder.of(QualificationRoleType.CLIENT,
            Long.toString(review.clientId()), QualificationType.INDIVIDUAL_KYC).status(QualificationStatus.REVOKED));

        decide(review, ClientReviewAction.REJECT, ClientReviewDecisionRequestBuilder.template().build())
            .assertAccepted(review.caseId(), ClientReviewAction.REJECT.getCaseStatus());

        store.assertUnchanged(revoked);
    }

    /** 审核结论投影的期望：REVIEWER 主任务决定、有效期起于结论时间、不带依据。 */
    private static ExpectedChange reviewed(MockedReview review, QualificationStatus status, ClientReviewDecisionRequestBody request,
        QualificationStatus fromStatus, long version, Instant started) {
        return new ExpectedChange(CLIENT, Long.toString(review.clientId()), review.main().qualificationType().getCode(), status,
            QualificationDecisionSource.REVIEWER.getCode(), review.caseId(), null, null, request.getReasonCode(), null, ACTOR,
            DecideClientReviewProcessor.TRIGGER_TYPE, fromStatus, version, started);
    }

    private ClientReviewCommandResponseChecker decide(MockedReview review, ClientReviewAction action,
        ClientReviewDecisionRequestBody body) {
        HttpHeaders headers = TestRequestHeaders.internal("qualification-projection-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "qualification-test-session");
        String path = action == ClientReviewAction.APPROVE ? "/approval" : "/rejection";
        return new ClientReviewCommandResponseChecker(http.post(CASES + review.caseId() + path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
