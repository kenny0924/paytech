package com.paytech.compliance.auto.ext.http;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.individual;
import static com.paytech.compliance.test.builder.ClientReviewCaseBuilder.organization;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.checker.ClientReviewResultResponseChecker;
import com.paytech.compliance.test.mocker.ClientReviewMocker;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;

/**
 * Client 申请的客户可见审核结果
 * <li>进行中：只返回任务状态。</li>
 * <li>已结束：退回返回分区与说明，通过/拒绝返回结果；内部理由不返回。</li>
 * <li>未找到：申请从未登记或只有非 Client 入驻任务 404；路径 ID 非法、带 Query 400。</li>
 *
 * @see ClientReviewResultApi#result(String, String)
 */
class ClientReviewResultApiAutoTest extends ComplianceMockTestBase {
    private static final String PATH = "/client-onboarding-review/v1/applications/%s/result";

    @Autowired
    private ClientReviewMocker reviews;

    /** 刚登记的任务仍在审核中。 */
    @Test
    void test_normalFlow() {
        MockedReview review = reviews.mock(organization());

        read(review.applicationId()).assertInProgress(review);
    }

    /** 退回、通过、拒绝后分别返回对应结果。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_normalFlow_closed(ClientReviewAction action) {
        MockedReview review = reviews.mock(individual().readyForApproval().closed(action));

        read(review.applicationId()).assertClosed(review, action);
    }

    /** 申请没有任务，或只有其他命名空间的任务。 */
    @Test
    void test_noDataFound() {
        MockedReview partner = reviews.mock(individual().sourceNamespace("CCT_PARTNER_APPLICATION"));

        read(999999999999999L).assertProblem(404, "CLIENT_REVIEW_NOT_FOUND");
        read(partner.applicationId()).assertProblem(404, "CLIENT_REVIEW_NOT_FOUND");
    }

    /** 不接受 Query。 */
    @Test
    void test_invalidParameter_query() {
        MockedReview review = reviews.mock(organization());

        invoke(PATH.formatted(review.applicationId()) + "?status=APPROVED").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径中的申请 ID 格式非法。 */
    @ParameterizedTest(name = "applicationId={0}")
    @MethodSource("invalidApplicationIds")
    void test_invalidParameter(String applicationId) {
        invoke(PATH.formatted(applicationId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<String> invalidApplicationIds() {
        return Stream.of("0", "-1", "abc", "012", "9223372036854775808");
    }

    private ClientReviewResultResponseChecker read(long applicationId) {
        return invoke(PATH.formatted(applicationId));
    }

    private ClientReviewResultResponseChecker invoke(String path) {
        String requestId = "risk-review-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-console-bff");
        return new ClientReviewResultResponseChecker(http.get(path, headers), objectMapper, requestId);
    }
}
