package com.paytech.risk.auto.ext.http.compliance;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.api.enums.MaterialQualificationType.INDIVIDUAL_KYC;
import static com.paytech.risk.api.enums.MaterialQualificationType.ORGANIZATION_KYB;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.SUBMITTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.individual;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.organization;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.compliance.ClientReviewAdminApi;
import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.checker.compliance.ClientReviewCaseResponseChecker;
import com.paytech.risk.test.checker.compliance.ClientReviewStoreChecker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedReview;

/**
 * O01 Client 入驻审核任务列表
 * <li>正常流程：只列 Client 入驻主任务（不含关联人员子任务与其他来源），按提交时间倒序、同时间按任务 ID 倒序；缺省第 1 页 20 条。</li>
 * <li>筛选：状态、资格类型、申请 ID 各自及组合生效；摘要不带主体、分类与门禁。</li>
 * <li>未找到：筛选无匹配时返回空页。</li>
 * <li>参数：分页越界或非数字、状态/资格类型非大写代码、申请 ID 非正整数，均在查询前拒绝（400）。</li>
 * <li>共享库中可能有其他任务：本例任务使用远期提交时间排在最前，总数按同条件下其他任务数加本例任务数核对。</li>
 *
 * @see ClientReviewAdminApi#listCases(String, String, Integer, Integer, String, String, String)
 */
class ClientReviewCaseListApiAutoTest extends MockTestBase {
    private static final String PATH = "/client-onboarding-admin/v1/cases";

    /** 远期提交时间，保证本例任务排在倒序列表最前。 */
    private static final Instant FUTURE = Instant.parse("2099-01-01T00:00:00.123456Z");

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private ClientReviewStoreChecker store;

    /** 按申请筛选：组织提交只列主任务一条，缺省分页为第 1 页 20 条。 */
    @Test
    void test_normalFlow() {
        MockedReview review = reviews.mock(organization());

        list("applicationId=" + review.applicationId()).assertPage(1, 20, 1).assertItems(review);
    }

    /** 已结束的任务同样列出，带结束时间、客户可见说明与退回分区。 */
    @Test
    void test_normalFlow_closedCase() {
        MockedReview review = reviews.mock(organization().closed(ClientReviewAction.RETURN));

        list("applicationId=" + review.applicationId()).assertPage(1, 20, 1).assertItems(review);
    }

    /** 按提交时间倒序分页，同一时间按任务 ID 倒序。 */
    @Test
    void test_normalFlow_orderingAndPaging() {
        MockedReview oldest = reviews.mock(individual().submittedTime(FUTURE));
        MockedReview tiedLow = reviews.mock(organization().submittedTime(FUTURE.plusSeconds(1)));
        MockedReview tiedHigh = reviews.mock(individual().submittedTime(FUTURE.plusSeconds(1)));
        MockedReview newest = reviews.mock(organization().submittedTime(FUTURE.plusSeconds(2)));
        MockedReview[] tied = tiedLow.caseId() > tiedHigh.caseId() ? new MockedReview[] {tiedLow, tiedHigh}
            : new MockedReview[] {tiedHigh, tiedLow};
        long total = store.otherClientCases(null, null, oldest, tiedLow, tiedHigh, newest) + 4;

        list("page=1&size=2").assertPage(1, 2, total).assertItems(newest, tied[0]);
        list("page=2&size=2").assertPage(2, 2, total).assertLeadingItems(tied[1], oldest);
    }

    /** 状态、资格类型筛选各自生效，并可与申请 ID 组合。 */
    @Test
    void test_normalFlow_filters() {
        MockedReview submitted = reviews.mock(organization().submittedTime(FUTURE));
        MockedReview approved = reviews.mock(organization().readyForApproval().closed(ClientReviewAction.APPROVE)
            .submittedTime(FUTURE));
        MockedReview individual = reviews.mock(individual().submittedTime(FUTURE));
        String approvedStatus = ClientReviewAction.APPROVE.getCaseStatus();

        list("status=" + approvedStatus).assertPage(1, 20, store.otherClientCases(approvedStatus, null, approved) + 1)
            .assertLeadingItems(approved);
        list("qualificationType=" + INDIVIDUAL_KYC.getCode())
            .assertPage(1, 20, store.otherClientCases(null, INDIVIDUAL_KYC.getCode(), individual) + 1)
            .assertLeadingItems(individual);
        list("status=" + SUBMITTED + "&qualificationType=" + ORGANIZATION_KYB.getCode() + "&applicationId="
            + submitted.applicationId()).assertPage(1, 20, 1).assertItems(submitted);
        list("status=" + approvedStatus + "&applicationId=" + submitted.applicationId()).assertPage(1, 20, 0).assertItems();
        list("qualificationType=" + INDIVIDUAL_KYC.getCode() + "&applicationId=" + approved.applicationId())
            .assertPage(1, 20, 0).assertItems();
    }

    /** 页码与每页条数的合法上界。 */
    @Test
    void test_edgeCase_pagingBounds() {
        MockedReview review = reviews.mock(organization());

        list("applicationId=" + review.applicationId() + "&size=100").assertPage(1, 100, 1).assertItems(review);
        list("applicationId=" + review.applicationId() + "&page=100000&size=1").assertPage(100000, 1, 1).assertItems();
        list("applicationId=" + review.applicationId() + "&page=1&size=1").assertPage(1, 1, 1).assertItems(review);
    }

    /** 不属于 Client 入驻的案件与筛选无匹配时返回空页。 */
    @Test
    void test_noDataFound() {
        MockedReview partner = reviews.mock(individual().sourceNamespace("CCT_PARTNER_APPLICATION"));

        list("applicationId=" + partner.applicationId()).assertPage(1, 20, 0).assertItems();
        list("applicationId=999999999999999").assertPage(1, 20, 0).assertItems();
        list("status=NO_SUCH_STATUS").assertPage(1, 20, 0).assertItems();
        list("qualificationType=ENTITY_UNDERWRITING").assertPage(1, 20, 0).assertItems();
    }

    /** 整秒提交时间同样输出固定六位小数。 */
    @Test
    void test_edgeCase_wholeSecondTime() {
        MockedReview review = reviews.mock(organization().submittedTime(Instant.parse("2026-10-13T08:00:00Z")));

        list("applicationId=" + review.applicationId()).assertPage(1, 20, 1).assertItems(review);
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        String requestId = "risk-review-" + UUID.randomUUID();

        new ClientReviewCaseResponseChecker(http.exchange(HttpMethod.GET, PATH, headers(requestId), "{}"), objectMapper,
            requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 查询参数非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        reviews.mock(organization());

        list(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("page 为 0", "page=0"), Arguments.of("page 超过 100000", "page=100001"),
            Arguments.of("page 非数字", "page=one"), Arguments.of("size 为 0", "size=0"),
            Arguments.of("size 超过 100", "size=101"), Arguments.of("size 为小数", "size=1.5"),
            Arguments.of("status 小写", "status=submitted"), Arguments.of("status 为空", "status="),
            Arguments.of("status 65 位", "status=S" + "0".repeat(64)),
            Arguments.of("qualificationType 小写", "qualificationType=individual_kyc"),
            Arguments.of("applicationId 为 0", "applicationId=0"), Arguments.of("applicationId 非数字", "applicationId=abc"),
            Arguments.of("applicationId 前导零", "applicationId=012"),
            Arguments.of("applicationId 20 位", "applicationId=" + "1".repeat(20)),
            Arguments.of("applicationId 超出 BIGINT", "applicationId=9223372036854775808"),
            Arguments.of("未知参数", "keyword=x"), Arguments.of("重复参数", "page=1&page=1"),
            Arguments.of("applicationId 为空", "applicationId="));
    }

    private ClientReviewCaseResponseChecker list(String query) {
        String requestId = "risk-review-" + UUID.randomUUID();
        return new ClientReviewCaseResponseChecker(http.get(query.isEmpty() ? PATH : PATH + "?" + query, headers(requestId)),
            objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}
