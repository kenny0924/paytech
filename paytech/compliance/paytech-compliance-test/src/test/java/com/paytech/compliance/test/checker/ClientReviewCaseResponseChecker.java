package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.compliance.api.enums.ClientReviewErrorCode;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedCase;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedClassification;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedEvidence;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedRequirement;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * O01 审核任务列表与 O02 任务详情的 HTTP 响应 Checker
 * <li>逐字段核对 Mocker 插入的事实：ID 与版本为十进制字符串，时间为 UTC ISO-8601；列表摘要的主体、分类与门禁为空数组。</li>
 * <li>详情的主体按主任务、子任务 ID 升序；材料要求按要求代码；依赖类型按依赖登记顺序。</li>
 *
 * @author Kenny
 */
public final class ClientReviewCaseResponseChecker extends AbstractHttpResponseChecker<ClientReviewCaseResponseChecker> {
    /** 任务摘要与详情共有的全部字段。 */
    static final Set<String> CASE_FIELDS = Set.of("caseId", "caseNo", "applicationId", "clientId", "sourceSubmissionId",
        "qualificationType", "status", "version", "submittedAt", "decidedAt", "publicNote", "returnSection", "subjects",
        "classifications", "approvalBlockers");

    public ClientReviewCaseResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected ClientReviewCaseResponseChecker self() {
        return this;
    }

    /** 分页元数据。 */
    public ClientReviewCaseResponseChecker assertPage(int page, int size, long total) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("items", "page", "size", "total"), Set.copyOf(body.propertyNames())),
            () -> assertEquals(page, body.path("page").asInt(), "page"),
            () -> assertEquals(size, body.path("size").asInt(), "size"),
            () -> assertEquals(total, body.path("total").asLong(), "total"));
        return this;
    }

    /** 当前页恰好按给定顺序包含这些主任务摘要。 */
    public ClientReviewCaseResponseChecker assertItems(MockedReview... expected) {
        JsonNode items = body().path("items");
        assertEquals(expected.length, items.size(), "当前页任务数");
        for (int i = 0; i < expected.length; i++) {
            assertCase(expected[i], items.get(i), "第 " + i + " 项");
            JsonNode item = items.get(i);
            assertAll("列表摘要不含详情", () -> assertEquals(0, item.path("subjects").size()),
                () -> assertTrue(item.path("subjects").isArray()), () -> assertTrue(item.path("classifications").isArray()),
                () -> assertEquals(0, item.path("classifications").size()),
                () -> assertTrue(item.path("approvalBlockers").isArray()),
                () -> assertEquals(0, item.path("approvalBlockers").size()));
        }
        return this;
    }

    /** 当前页的前若干项依次为给定任务（其余为其他数据）。 */
    public ClientReviewCaseResponseChecker assertLeadingItems(MockedReview... expected) {
        JsonNode items = body().path("items");
        assertTrue(items.size() >= expected.length, "当前页任务数不足");
        for (int i = 0; i < expected.length; i++) {
            assertCase(expected[i], items.get(i), "第 " + i + " 项");
        }
        return this;
    }

    /**
     * 详情：主任务、全部主体及材料、分类与通过门禁
     *
     * @param review 插入的审核任务
     * @param approvalBlockers 期望的门禁未满足项，按策略顺序
     * @return 当前 Checker
     */
    public ClientReviewCaseResponseChecker assertDetail(MockedReview review, String... approvalBlockers) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        JsonNode body = body();
        assertCase(review, body, "详情");
        assertEquals(List.of(approvalBlockers), strings(body.path("approvalBlockers")), "通过门禁");
        JsonNode subjects = body.path("subjects");
        assertEquals(review.cases().size(), subjects.size(), "主体数");
        for (int i = 0; i < review.cases().size(); i++) {
            assertSubject(review.cases().get(i), subjects.get(i));
        }
        JsonNode classifications = body.path("classifications");
        assertEquals(review.classifications().size(), classifications.size(), "分类数");
        for (int i = 0; i < review.classifications().size(); i++) {
            MockedClassification expected = review.classifications().get(i);
            JsonNode actual = classifications.get(i);
            assertAll("分类 " + i,
                () -> assertEquals(Set.of("industryCode", "mccCode", "primary"), Set.copyOf(actual.propertyNames())),
                () -> assertEquals(expected.industryCode(), actual.path("industryCode").asString()),
                () -> assertEquals(expected.mccCode(), actual.path("mccCode").asString()),
                () -> assertEquals(expected.primary(), actual.path("primary").asBoolean()));
        }
        return this;
    }

    /** 任务不存在或不属于 Client 入驻。 */
    public ClientReviewCaseResponseChecker assertNotFound() {
        return assertProblem(404, ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND.getCode());
    }

    /** 主任务的摘要字段。 */
    void assertCase(MockedReview review, JsonNode actual, String label) {
        MockedCase main = review.main();
        assertAll(label, () -> assertEquals(CASE_FIELDS, Set.copyOf(actual.propertyNames())),
            () -> assertEquals(Long.toString(review.caseId()), actual.path("caseId").asString()),
            () -> assertEquals(review.caseNo(), actual.path("caseNo").asString()),
            () -> assertEquals(Long.toString(review.applicationId()), actual.path("applicationId").asString()),
            () -> assertEquals(Long.toString(review.clientId()), actual.path("clientId").asString()),
            () -> assertEquals(review.submissionId(), actual.path("sourceSubmissionId").asString()),
            () -> assertEquals(main.qualificationType().getCode(), actual.path("qualificationType").asString()),
            () -> assertEquals(main.status(), actual.path("status").asString()),
            () -> assertEquals(Long.toString(main.version()), actual.path("version").asString()),
            () -> assertTime(review.spec().submittedTime(), actual.path("submittedAt")),
            () -> assertTime(review.spec().decisionTime(), actual.path("decidedAt")),
            () -> assertText(review.spec().publicNote(), actual.path("publicNote")),
            () -> assertText(review.spec().returnSection(), actual.path("returnSection")));
    }

    /** 一个任务的主体、依赖类型与材料。 */
    void assertSubject(MockedCase expected, JsonNode actual) {
        assertAll("主体 " + expected.caseId(),
            () -> assertEquals(Set.of("caseId", "caseStatus", "partyId", "profileId", "roleType", "qualificationType",
                "dependencyTypes", "requirements"), Set.copyOf(actual.propertyNames())),
            () -> assertEquals(Long.toString(expected.caseId()), actual.path("caseId").asString()),
            () -> assertEquals(expected.status(), actual.path("caseStatus").asString()),
            () -> assertEquals(Long.toString(expected.partyId()), actual.path("partyId").asString()),
            () -> assertEquals(Long.toString(expected.profileId()), actual.path("profileId").asString()),
            () -> assertEquals(expected.roleType(), actual.path("roleType").asString()),
            () -> assertEquals(expected.qualificationType().getCode(), actual.path("qualificationType").asString()),
            () -> assertEquals(expected.dependencyTypes(), strings(actual.path("dependencyTypes"))));
        JsonNode requirements = actual.path("requirements");
        assertEquals(expected.requirements().size(), requirements.size(), "材料要求数");
        for (int i = 0; i < expected.requirements().size(); i++) {
            assertRequirement(expected.requirements().get(i), requirements.get(i));
        }
    }

    /** 一个材料要求及其证据；同一要求下证据按 ID 比较。 */
    void assertRequirement(MockedRequirement expected, JsonNode actual) {
        assertAll("材料要求 " + expected.requirementCode(),
            () -> assertEquals(Set.of("requirementId", "requirementCode", "documentType", "required", "status", "evidence"),
                Set.copyOf(actual.propertyNames())),
            () -> assertEquals(Long.toString(expected.requirementId()), actual.path("requirementId").asString()),
            () -> assertEquals(expected.requirementCode(), actual.path("requirementCode").asString()),
            () -> assertEquals(expected.documentType(), actual.path("documentType").asString()),
            () -> assertEquals(expected.required(), actual.path("required").asBoolean()),
            () -> assertEquals(expected.status(), actual.path("status").asString()));
        List<String> wanted = new ArrayList<>();
        for (MockedEvidence evidence : expected.evidence()) {
            wanted.add(evidence.evidenceId() + ":" + evidence.fileId() + ":" + evidence.status());
        }
        List<String> got = new ArrayList<>();
        actual.path("evidence").forEach(evidence -> {
            assertEquals(Set.of("evidenceId", "fileId", "status"), Set.copyOf(evidence.propertyNames()));
            got.add(evidence.path("evidenceId").asString() + ":" + evidence.path("fileId").asString() + ":"
                + evidence.path("status").asString());
        });
        assertEquals(wanted.stream().sorted().toList(), got.stream().sorted().toList(), "证据");
    }

    /** 合同时间格式：UTC、固定六位小数。 */
    static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    /** UTC 固定六位小数时间；空值为 JSON null。 */
    static void assertTime(Instant expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "时间应为 null");
            return;
        }
        assertEquals(TIME.format(expected), actual.asString(), "时间");
    }

    /** 可空文本；空值为 JSON null。 */
    static void assertText(String expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "应为 null");
            return;
        }
        assertEquals(expected, actual.asString());
    }

    static List<String> strings(JsonNode array) {
        assertTrue(array.isArray(), "应为数组");
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
