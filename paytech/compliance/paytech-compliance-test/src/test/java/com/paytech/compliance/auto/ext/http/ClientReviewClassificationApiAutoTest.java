package com.paytech.risk.auto.ext.http.compliance;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLASSIFICATION_CONFLICT;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.risk.api.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.SUBMITTED;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.individual;
import static com.paytech.risk.test.builder.compliance.ClientReviewCaseBuilder.organization;
import static com.paytech.risk.test.builder.compliance.ClientReviewClassificationsRequestBuilder.CONFIRMED_AT;
import static com.paytech.risk.test.builder.compliance.ClientReviewClassificationsRequestBuilder.CONFIRMED_BY;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

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
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody;
import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.compliance.ClientReviewClassificationsRequestBuilder;
import com.paytech.risk.test.checker.compliance.ClientReviewCommandResponseChecker;
import com.paytech.risk.test.checker.compliance.ClientReviewStoreChecker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedReview;

/**
 * I02 接收 CCT 已确认的任务分类
 * <li>正常流程：进行中的主任务接收一个主要分类加若干次要分类，分类行落库且主任务版本递增；确认时间按微秒保存。</li>
 * <li>重放：已接收后同集合、同确认人、同确认时间（顺序不同或纳秒差异也一样）返回当前结果；任务已结束时同样只做重放判断。</li>
 * <li>未找到：任务不存在、是子任务、不属于 Client 入驻，或申请/提交编号与任务不符，一律 404 不暴露其他任务。</li>
 * <li>参数：字段格式由入口校验拒绝（422）；路径 ID、确认时间格式与报文在接收前拒绝（400），非 JSON 415。</li>
 * <li>业务规则：主要分类不是恰好一个、行业与 MCC 组合重复 400；已接收不同集合/确认人/确认时间 409 冲突；任务已结束 409。</li>
 *
 * @see ClientReviewRegistrationApi#receiveClassifications(String, String, ClientReviewClassificationsRequestBody)
 */
class ClientReviewClassificationApiAutoTest extends MockTestBase {
    private static final String PATH = "/client-onboarding-review/v1/cases/%s/classifications";

    @Autowired
    private ClientReviewMocker reviews;

    @Autowired
    private ClientReviewStoreChecker store;

    /** 一个主要分类加两个次要分类全部落库，主任务版本递增，状态不变。 */
    @Test
    void test_normalFlow() {
        MockedReview review = reviews.mock(organization());
        ClientReviewClassificationsRequestBody request = template(review).add("SOFTWARE", "5734", false)
            .add("RETAIL", "5999", false).build();

        receive(review, request).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().classifications(request));
    }

    /** 同集合、同确认人、同确认时间重放返回当前结果，集合顺序不同也视为相同，不新增行也不再改变版本。 */
    @Test
    void test_normalFlow_replay() {
        MockedReview review = reviews.mock(organization());
        ClientReviewClassificationsRequestBody request = template(review).add("SOFTWARE", "5734", false).build();
        receive(review, request).assertAccepted(review.caseId(), SUBMITTED);

        receive(review, request).assertAccepted(review.caseId(), SUBMITTED);
        ClientReviewClassificationsRequestBody reordered = template(review).clear().add("SOFTWARE", "5734", false)
            .add("RETAIL", "5411", true).build();
        receive(review, reordered).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().classifications(request));
    }

    /** 确认时间超出微秒的部分被截断，以截断后的时间判断重放。 */
    @Test
    void test_edgeCase_confirmedAtTruncatedToMicros() {
        MockedReview review = reviews.mock(individual());
        ClientReviewClassificationsRequestBody request = template(review).confirmedAt("2026-10-13T09:00:00.1234567Z").build();
        receive(review, request).assertAccepted(review.caseId(), SUBMITTED);

        receive(review, template(review).confirmedAt("2026-10-13T09:00:00.123456Z").build())
            .assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().classifications(request));
    }

    /** 分类集合达到 20 条上限时完整接收。 */
    @Test
    void test_edgeCase_maximumClassifications() {
        MockedReview review = reviews.mock(individual());
        ClientReviewClassificationsRequestBuilder builder = template(review);
        for (int i = 1; i < 20; i++) {
            builder.add("INDUSTRY_" + i, "%04d".formatted(i), false);
        }
        ClientReviewClassificationsRequestBody request = builder.build();

        receive(review, request).assertAccepted(review.caseId(), SUBMITTED);

        store.assertStore(store.expect(review).mainTouched().classifications(request));
    }

    /** 已以任一结论结束、但此前已接收分类的任务，同内容重放仍返回当前结果。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_edgeCase_replayAfterClosed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().classification("RETAIL", "5411", true)
            .confirmed(Long.parseLong(CONFIRMED_BY), Instant.parse(CONFIRMED_AT)).closed(action));

        receive(review, template(review).build()).assertAccepted(review.caseId(), action.getCaseStatus());

        store.assertUnchanged(review);
    }

    /** 任务不存在、是子任务或不属于 Client 入驻时 404。 */
    @Test
    void test_noDataFound() {
        MockedReview review = reviews.mock(organization());
        MockedReview partner = reviews.mock(individual().sourceNamespace("CCT_PARTNER_APPLICATION"));

        invoke(PATH.formatted("999999999999999"), template(review).build()).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        invoke(PATH.formatted(review.child(0).caseId()), template(review).build())
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        receive(partner, template(partner).build()).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);

        store.assertUnchanged(review);
        store.assertUnchanged(partner);
    }

    /** 申请或提交编号与任务不符时按不存在处理，不暴露其他任务。 */
    @Test
    void test_noDataFound_identityMismatch() {
        MockedReview review = reviews.mock(organization());
        MockedReview other = reviews.mock(organization());

        receive(review, template(review).applicationId(Long.toString(other.applicationId())).build())
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        receive(review, template(review).submissionId(other.submissionId()).build())
            .assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);
        receive(review, template(other).build()).assertReviewError(404, CLIENT_REVIEW_NOT_FOUND);

        store.assertUnchanged(review);
        store.assertUnchanged(other);
    }

    /** 字段格式由入口校验拒绝并定位字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter(String field, String scenario,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        MockedReview review = reviews.mock(organization());

        receive(review, change.apply(template(review)).build()).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError(field);

        store.assertUnchanged(review);
    }

    /** 路径中的任务 ID 格式非法。 */
    @ParameterizedTest(name = "caseId={0}")
    @MethodSource("invalidCaseIds")
    void test_invalidParameter_caseId(String caseId) {
        MockedReview review = reviews.mock(organization());

        invoke(PATH.formatted(caseId), template(review).build()).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(review);
    }

    /** 确认时间不是 ISO-8601 瞬时值。 */
    @Test
    void test_invalidParameter_confirmedAt() {
        MockedReview review = reviews.mock(organization());

        receive(review, template(review).confirmedAt("2026-10-13 09:00:00").build()).assertProblem(400,
            INVALID_REQUEST.getCode());

        store.assertUnchanged(review);
    }

    /** 无法解析或携带未知字段的报文，以及非 JSON 媒体类型。 */
    @Test
    void test_invalidParameter_malformedBody() {
        MockedReview review = reviews.mock(organization());
        String path = PATH.formatted(review.caseId());

        invoke(path, headers(), "{\"applicationId\":").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, headers(), "").assertProblem(400, INVALID_REQUEST.getCode());
        invoke(path, headers(), objectMapper.writeValueAsString(template(review).build())
            .replaceFirst("\\{", "{\"industry\":\"RETAIL\",")).assertProblem(400, INVALID_REQUEST.getCode());
        HttpHeaders text = headers();
        text.setContentType(MediaType.TEXT_PLAIN);
        invoke(path, text, objectMapper.writeValueAsString(template(review).build())).assertProblem(415,
            UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertUnchanged(review);
    }

    /** 主要分类不是恰好一个，或行业与 MCC 组合重复。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSets")
    void test_businessRuleViolation_invalidSet(String scenario,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        MockedReview review = reviews.mock(organization());

        receive(review, change.apply(template(review)).build()).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());

        store.assertUnchanged(review);
    }

    /** 格式合法但超出 signed BIGINT 的 ID 在入口转换时拒绝，不落库。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("overflowIds")
    void test_invalidParameter_idOverflow(String field,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        MockedReview review = reviews.mock(organization());

        receive(review, change.apply(template(review)).build()).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(review);
    }

    /** 已接收后改集合、确认人或确认时间：409 冲突，已接收的分类保持不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("conflicts")
    void test_businessRuleViolation_conflict(String scenario,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        MockedReview review = reviews.mock(organization().classification("RETAIL", "5411", true)
            .confirmed(Long.parseLong(CONFIRMED_BY), Instant.parse(CONFIRMED_AT)));

        receive(review, change.apply(template(review)).build())
            .assertReviewError(409, CLIENT_REVIEW_CLASSIFICATION_CONFLICT);

        store.assertUnchanged(review);
    }

    /** 已结束且未接收分类的任务不能再接收。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ClientReviewAction.class)
    void test_businessRuleViolation_closed(ClientReviewAction action) {
        MockedReview review = reviews.mock(organization().closed(action));

        receive(review, template(review).build()).assertReviewError(409, CLIENT_REVIEW_CLOSED);

        store.assertUnchanged(review);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(invalid("applicationId", "缺失", b -> b.applicationId(null)),
            invalid("applicationId", "前导零", b -> b.applicationId("012")),
            invalid("submissionId", "非 UUID", b -> b.submissionId("submission-1")),
            invalid("submissionId", "大写 UUID", b -> b.submissionId(UUID.randomUUID().toString().toUpperCase())),
            invalid("confirmedBy", "零", b -> b.confirmedBy("0")),
            invalid("confirmedBy", "缺失", b -> b.confirmedBy(null)),
            invalid("confirmedAt", "空白", b -> b.confirmedAt(" ")),
            invalid("confirmedAt", "超过 40 位", b -> b.confirmedAt("2".repeat(41))),
            invalid("classifications", "空集合", ClientReviewClassificationsRequestBuilder::clear),
            invalid("classifications", "缺失", ClientReviewClassificationsRequestBuilder::nullClassifications),
            invalid("classifications", "超过 20 条", ClientReviewClassificationApiAutoTest::twentyOne),
            invalid("classifications[0].industryCode", "小写", b -> b.clear().add("retail", "5411", true)),
            invalid("classifications[0].industryCode", "65 位", b -> b.clear().add("R" + "0".repeat(64), "5411", true)),
            invalid("classifications[0].mccCode", "三位", b -> b.clear().add("RETAIL", "541", true)),
            invalid("classifications[0].mccCode", "非数字", b -> b.clear().add("RETAIL", "54A1", true)),
            invalid("classifications[0].primary", "缺失", b -> b.clear().add("RETAIL", "5411", null)));
    }

    static Stream<String> invalidCaseIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20));
    }

    static Stream<Arguments> invalidSets() {
        return Stream.of(set("没有主要分类", b -> b.clear().add("RETAIL", "5411", false)),
            set("两个主要分类", b -> b.add("SOFTWARE", "5734", true)),
            set("次要分类的行业与 MCC 组合重复", b -> b.add("SOFTWARE", "5734", false).add("SOFTWARE", "5734", false)),
            set("同一组合一主一次", b -> b.add("RETAIL", "5411", false)));
    }

    static Stream<Arguments> overflowIds() {
        return Stream.of(set("applicationId", b -> b.applicationId("9223372036854775808")),
            set("confirmedBy", b -> b.confirmedBy("9223372036854775808")));
    }

    static Stream<Arguments> conflicts() {
        return Stream.of(set("多一个次要分类", b -> b.add("SOFTWARE", "5734", false)),
            set("主要分类不同", b -> b.clear().add("SOFTWARE", "5734", true)),
            set("同组合但主次不同", b -> b.clear().add("RETAIL", "5411", false).add("SOFTWARE", "5734", true)),
            set("确认人不同", b -> b.confirmedBy("930000002")),
            set("确认时间不同", b -> b.confirmedAt("2026-10-13T09:00:00.123457Z")));
    }

    private static ClientReviewClassificationsRequestBuilder twentyOne(ClientReviewClassificationsRequestBuilder builder) {
        for (int i = 1; i <= 20; i++) {
            builder.add("INDUSTRY_" + i, "%04d".formatted(i), false);
        }
        return builder;
    }

    private static Arguments invalid(String field, String scenario,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        return Arguments.of(field, scenario, change);
    }

    private static Arguments set(String scenario,
        Function<ClientReviewClassificationsRequestBuilder, ClientReviewClassificationsRequestBuilder> change) {
        return Arguments.of(scenario, change);
    }

    /** 与任务申请、提交编号一致的合法请求。 */
    private static ClientReviewClassificationsRequestBuilder template(MockedReview review) {
        return ClientReviewClassificationsRequestBuilder.template(review.applicationId(), review.submissionId());
    }

    private ClientReviewCommandResponseChecker receive(MockedReview review, ClientReviewClassificationsRequestBody body) {
        return invoke(PATH.formatted(review.caseId()), body);
    }

    private ClientReviewCommandResponseChecker invoke(String path, Object body) {
        return invoke(path, headers(), body);
    }

    private ClientReviewCommandResponseChecker invoke(String path, HttpHeaders headers, Object body) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new ClientReviewCommandResponseChecker(http.post(path, headers, body), objectMapper, requestId);
    }

    /** CCT 私网调用：内部 JSON 请求头加调用方标识。 */
    private static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-review-" + UUID.randomUUID());
        headers.set("X-Caller-Service", "client-contract-service");
        return headers;
    }
}
