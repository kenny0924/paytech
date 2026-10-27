package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.compliance.ext.job.ClientReviewReturnExpiryJobHandler;
import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 退回、重提串联与退回超期（AC-07，technique S-01、S-14）
 * <li>退回：主 / 子案件 RETURNED 释放进行中占位，登记需求级错误与补件期限，发布 ClientReviewDecided v2（errors、dueTime）；
 * Client 结果接口返回错误与期限。</li>
 * <li>重提：新提交编号 + previousCaseId 新建第 2 轮案件；上一轮已接受材料与有效 PASS 检查复用（标记来源案件），新材料待核验。</li>
 * <li>超期：扫描把补件期限已过且未重提的 RETURNED 案件置 EXPIRED 并发布 EXPIRED；已重提的案件不再过期。</li>
 */
class ClientReviewReturnApiAutoTest extends ClientReviewFlowTestBase {
    @Autowired
    private ClientReviewReturnExpiryJobHandler expiryJob;

    /** 三国程序，筛查默认无命中。 */
    @BeforeEach
    void setUpReturn() {
        programs.mockCountryPacks();
        screening.mockNoHitsByDefault();
    }

    /** 退回 → 事件与结果 → 重提第 2 轮并复用。 */
    @Test
    void test_normalFlow_returnAndResubmit() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("AE");
        String passport = builder.addDocument(builder.primaryPartyId(), "IDENTITY", "PASSPORT");
        builder.addDocument(builder.primaryPartyId(), "BANK", "BANK_PROOF");
        mockSubjects(builder, "Returned");
        long first = register(builder);
        acceptAllEvidence(first);
        passAllChecks(first);
        String applicationId = builder.body().getApplicationId();

        returnCase(first, builder.primaryPartyId(), 14).assertCommand(first, "RETURNED");

        Map<String, Object> returned = reviews.caseRow(first);
        JsonNode event = awaitEvent("ClientReviewDecided", first, "outcome", "RETURNED");
        JsonNode visible = result(applicationId).ok();
        assertAll(() -> assertNull(returned.get("active_slot")), () -> assertNotNull(returned.get("review_due_time")),
            () -> assertEquals(1, reviews.returnErrors(first).size()),
            () -> assertEquals("DOCUMENT_TOO_OLD", event.path("errors").path(0).path("code").asString()),
            () -> assertEquals("UPLOAD_DOCUMENT", event.path("errors").path(0).path("remediation").asString()),
            () -> assertTrue(event.path("dueTime").isString()),
            () -> assertTrue(event.path("decisionId").isNull()),
            () -> assertEquals("RETURNED", visible.path("outcome").asString()),
            () -> assertEquals("document.BANK", visible.path("errors").path(0).path("requirementKey").asString()),
            () -> assertTrue(visible.path("dueTime").isString()),
            () -> assertEquals(1, visible.path("roundNo").asInt()));

        builder.body().getDocuments().removeLast();
        String newBank = builder.addDocument(builder.primaryPartyId(), "BANK", "BANK_PROOF");
        long second = Long.parseLong(submit(builder.resubmit(first).build()).ok().path("caseId").asString());
        reviews.awaitScreeningSettled(second);

        Map<String, Object> round2 = reviews.caseRow(second);
        assertAll(() -> assertEquals(2, ((Number)round2.get("round_no")).intValue()),
            () -> assertEquals(first, number(round2.get("previous_case_id"))),
            () -> assertEquals("ACCEPTED", requirement(second, "IDENTITY").get("status")),
            () -> assertEquals(first, number(requirement(second, "IDENTITY").get("reused_from_case_id"))),
            () -> assertEquals("ACCEPTED", evidence(second, passport).get("status")),
            () -> assertEquals(first, number(evidence(second, passport).get("reused_from_case_id"))),
            () -> assertEquals("RECEIVED", evidence(second, newBank).get("status")),
            () -> assertEquals("RECEIVED", requirement(second, "BANK").get("status")),
            () -> reviews.checks(second).forEach(check -> assertEquals("COMPLETED", check.get("status"))),
            () -> reviews.checks(second).forEach(check -> assertEquals(first, number(check.get("reused_from_case_id")))),
            () -> assertStatus(first, "RETURNED"),
            () -> assertEquals(2, result(applicationId).ok().path("roundNo").asInt()),
            () -> assertEquals("SUBMITTED", result(applicationId).ok().path("status").asString()));

        submit(builder.resubmit(first).build()).assertProblem(400, "BUSINESS_CHECK_FAILED");
        reviews.expireDueTime(first);
        assertEquals(0, expiryJob.runOnce().getExpiredCount());
        assertStatus(first, "RETURNED");
    }

    /** 退回超期：RETURNED 主 / 子案件 EXPIRED，发布 EXPIRED；超期后不能重提。 */
    @Test
    void test_normalFlow_expiry() {
        var builder = ClientReviewRegistrationRequestBuilder.organization("EG", "LLC");
        long person = builder.addRelatedPerson("DIRECTOR");
        mockSubjects(builder, "Expiring Co");
        long caseId = register(builder);
        returnCase(caseId, person, 1).ok();
        long child = number(reviews.childCases(caseId).getFirst().get("case_id"));

        assertEquals(0, expiryJob.runOnce().getScannedCount());
        reviews.expireDueTime(caseId);
        var response = expiryJob.runOnce();

        assertAll(() -> assertEquals(1, response.getExpiredCount()), () -> assertStatus(caseId, "EXPIRED"),
            () -> assertStatus(child, "EXPIRED"),
            () -> assertEquals("EXPIRE", reviews.stateChanges(caseId).getLast().get("action_type")),
            () -> assertEquals("EXPIRED", awaitEvent("ClientReviewDecided", caseId, "outcome", "EXPIRED").path("outcome").asString()),
            () -> assertEquals("EXPIRED", result(builder.body().getApplicationId()).ok().path("outcome").asString()));
        submit(builder.resubmit(caseId).build()).assertProblem(400, "BUSINESS_CHECK_FAILED");
        withdraw(caseId, builder.body().getApplicationId()).assertProblem(409, "CLIENT_REVIEW_CLOSED");
    }

    /** 已退回、未重提的案件可撤回（S-14）。 */
    @Test
    void test_normalFlow_withdrawReturned() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Withdraw Returned");
        long caseId = register(builder);
        returnCase(caseId, builder.primaryPartyId(), 30).ok();

        withdraw(caseId, builder.body().getApplicationId()).assertCommand(caseId, "WITHDRAWN");
        assertEquals("WITHDRAW", reviews.stateChanges(caseId).getLast().get("action_type"));
    }

    /** 退回请求合同：错误、期限、说明必填；期限 1—90。 */
    @Test
    void test_invalidParameter() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Invalid Return");
        long caseId = register(builder);

        admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/return", Map.of("reasonCode", "X", "errors", List.of(), "dueDays", 5,
            "publicNote", "n")).assertProblem(422, "VALIDATION_ERROR");
        returnCase(caseId, builder.primaryPartyId(), 91).assertProblem(422, "VALIDATION_ERROR");
        assertStatus(caseId, "SUBMITTED");
    }

    /** 要求行。 */
    Map<String, Object> requirement(long caseId, String code) {
        return reviews.requirements(caseId).stream().filter(row -> code.equals(row.get("requirement_code"))).findFirst().orElseThrow();
    }

    /** 按文件地址的证据行。 */
    Map<String, Object> evidence(long caseId, String fileAddress) {
        return reviews.evidence(caseId).stream().filter(row -> fileAddress.equals(row.get("file_address"))).findFirst().orElseThrow();
    }
}
