package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.mocker.ProgramPackMocker;

import tools.jackson.databind.JsonNode;

/**
 * 运营案件工作台（AC-06、AC-08、AC-10、AC-13）
 * <li>首个审核命令把 SUBMITTED 推进为 IN_REVIEW；材料核验带拒绝原因与核验人；可豁免要求可豁免；人工检查录入结论与有效期；
 * 命中处置追加记录、关闭类结论关闭命中；手工 CRR 必填因子且清空推荐；推荐与决定四眼。</li>
 * <li>批准门禁未满足 422 并列出未满足项；批准冻结档次、评级、辖区、责任方、推荐人、决定人与代操作 PayFac，投影资格并发布
 * ClientReviewDecided v2 与 QualificationChanged。</li>
 *
 * @see ClientReviewAdminApi
 */
class ClientReviewWorkbenchApiAutoTest extends ClientReviewFlowTestBase {

    /** 三国程序，筛查默认无命中。 */
    @BeforeEach
    void setUpWorkbench() {
        programs.mockCountryPacks();
        screening.mockNoHitsByDefault();
    }

    /** 完整审核到批准：冻结字段、资格投影与两个事件。 */
    @Test
    void test_normalFlow_approve() {
        var builder = saOrganization(PAYFAC);
        long caseId = register(builder);
        long child = number(reviews.childCases(caseId).getFirst().get("case_id"));

        prepareForDecision(caseId, builder.build());
        assertEquals(List.of("RECOMMENDATION_MISSING"), blockers(DECIDER, caseId));
        recommend(REVIEWER, caseId, "APPROVE").ok();
        approve(REVIEWER, caseId, null).assertProblem(422, "CLIENT_REVIEW_NOT_READY");
        approve(DECIDER, caseId, PAYFAC).assertCommand(caseId, "APPROVED");

        Map<String, Object> decision = reviews.decisions(caseId).getFirst();
        Map<String, Object> qualification = reviews.qualification("CLIENT", builder.body().getClientId(), "ORGANIZATION_KYB");
        assertAll(() -> assertStatus(caseId, "APPROVED"), () -> assertStatus(child, "APPROVED"),
            () -> assertEquals("STANDARD", decision.get("tier")), () -> assertEquals("LOW", decision.get("risk_rating")),
            () -> assertEquals("SA", decision.get("jurisdiction_code")),
            () -> assertEquals(PAYFAC, number(decision.get("responsible_partner_id"))),
            () -> assertEquals(REVIEWER, number(decision.get("recommended_by"))),
            () -> assertEquals(DECIDER, number(decision.get("decided_by"))),
            () -> assertEquals(Boolean.FALSE, decision.get("four_eyes_waived")),
            () -> assertEquals("PLATFORM", decision.get("operated_by")),
            () -> assertEquals(PAYFAC, number(decision.get("on_behalf_of_partner_id"))),
            () -> assertEquals(1, reviews.decisions(child).size()),
            () -> assertEquals("APPROVED", qualification.get("status")),
            () -> assertEquals("LOW", qualification.get("risk_rating")),
            () -> assertEquals(36, ((Number)qualification.get("review_interval_months")).intValue()),
            () -> assertEquals("ACTIVE", qualification.get("review_status")),
            () -> assertNotNull(qualification.get("next_review_time")),
            () -> assertEquals(PAYFAC, number(qualification.get("responsible_partner_id"))),
            () -> assertEquals(PAYFAC, number(reviews.caseRow(caseId).get("on_behalf_of_partner_id"))),
            () -> assertEquals("PLATFORM", reviews.stateChanges(caseId).getLast().get("operated_by")));
        JsonNode decided = awaitEvent("ClientReviewDecided", caseId, "outcome", "APPROVED");
        JsonNode changed = awaitEvent("QualificationChanged", number(qualification.get("qualification_id")), "status", "APPROVED");
        assertAll(() -> assertEquals("STANDARD", decided.path("tier").asString()),
            () -> assertEquals("LOW", decided.path("riskRating").asString()),
            () -> assertEquals(1, decided.path("roundNo").asInt()),
            () -> assertEquals("ONBOARDING", decided.path("caseType").asString()),
            () -> assertEquals(0, decided.path("errors").size()),
            () -> assertEquals(Long.toString(DECIDER), decided.path("decidedBy").asString()),
            () -> assertEquals(builder.body().getClientId(), changed.path("roleId").asString()),
            () -> assertEquals("REVIEWER", changed.path("decisionSource").asString()),
            () -> assertEquals(Long.toString(PAYFAC), changed.path("responsiblePartnerId").asString()),
            () -> assertTrue(changed.path("previousStatus").isNull()));
        rate(caseId, "HIGH").assertProblem(409, "CLIENT_REVIEW_CLOSED");
    }

    /** 门禁：初始未满足项完整列出，批准 422；首个命令推进 IN_REVIEW 并记 START_REVIEW。 */
    @Test
    void test_businessRuleViolation_gate() {
        var builder = saOrganization(null);
        long caseId = register(builder);

        List<String> initial = blockers(DECIDER, caseId);
        assertTrue(initial.containsAll(List.of("EVIDENCE_PENDING", "REQUIRED_MATERIAL_NOT_ACCEPTED", "CLASSIFICATION_NOT_RECEIVED",
            "CHECK_NOT_PASSED", "RISK_RATING_MISSING", "RECOMMENDATION_MISSING")), initial.toString());
        approve(DECIDER, caseId, null).assertProblem(422, "CLIENT_REVIEW_NOT_READY");
        assertStatus(caseId, "SUBMITTED");

        rate(caseId, "MEDIUM").ok();
        assertAll(() -> assertStatus(caseId, "IN_REVIEW"),
            () -> assertEquals("START_REVIEW", reviews.stateChanges(caseId).getLast().get("action_type")),
            () -> assertEquals(REVIEWER, number(reviews.stateChanges(caseId).getLast().get("actor_id"))));
    }

    /** 材料拒绝必须带原因码，记录核验人；已核验为其他结果 409。 */
    @Test
    void test_normalFlow_evidenceReview() {
        var builder = saOrganization(null);
        long caseId = register(builder);
        Map<String, Object> evidence = reviews.evidence(caseId).getFirst();
        String path = "/cases/" + caseId + "/evidence/" + evidence.get("evidence_id") + "/review";

        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "REJECTED")).assertStatus(400);
        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "ACCEPTED", "rejectReasonCode", "OTHER")).assertStatus(400);
        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "REJECTED", "rejectReasonCode", "DOCUMENT_UNREADABLE",
            "note", "blurred")).assertCommand(caseId, "IN_REVIEW");
        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "ACCEPTED")).assertProblem(409, "CLIENT_REVIEW_EVIDENCE_REVIEWED");

        Map<String, Object> reviewed = reviews.evidence(caseId).getFirst();
        assertAll(() -> assertEquals("REJECTED", reviewed.get("status")),
            () -> assertEquals("DOCUMENT_UNREADABLE", reviewed.get("reject_reason_code")),
            () -> assertEquals(REVIEWER, number(reviewed.get("reviewer_id"))),
            () -> assertEquals("REJECTED", requirement(caseId, evidence.get("requirement_code").toString()).get("status")));
    }

    /** 只有可豁免要求能豁免；豁免幂等并记录豁免人与原因。 */
    @Test
    void test_normalFlow_waiver() {
        long caseId = register(saOrganization(null));
        Map<String, Object> registration = requirement(caseId, "REGISTRATION");
        Map<String, Object> constitution = requirement(caseId, "CONSTITUTION");

        waive(caseId, registration).assertProblem(400, "BUSINESS_CHECK_FAILED");
        waive(caseId, constitution).ok();
        waive(caseId, constitution).ok();

        Map<String, Object> waived = requirement(caseId, "CONSTITUTION");
        assertAll(() -> assertEquals("WAIVED", waived.get("status")),
            () -> assertEquals(REVIEWER, number(waived.get("waived_by"))),
            () -> assertEquals("SINGLE_SHAREHOLDER", waived.get("waived_reason_code")));
    }

    /** 人工检查：录入结论与有效期；同结论重放成功，异结论 409；阻断检查 FAIL 阻断批准。 */
    @Test
    void test_normalFlow_manualChecks() {
        long caseId = register(saOrganization(null));
        Map<String, Object> website = check(caseId, "WEBSITE_REVIEW");
        String path = "/cases/" + caseId + "/checks/" + website.get("check_id") + "/result";

        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "FAIL", "reasonCode", "REFUND_POLICY_MISSING")).ok();
        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "FAIL", "reasonCode", "REFUND_POLICY_MISSING")).ok();
        admin(REVIEWER, HttpMethod.POST, path, Map.of("outcome", "PASS", "reasonCode", "OK")).assertProblem(409,
            "CLIENT_REVIEW_CHECK_RECORDED");

        Map<String, Object> recorded = check(caseId, "WEBSITE_REVIEW");
        Instant validUntil = ((LocalDateTime)recorded.get("valid_until")).toInstant(ZoneOffset.UTC);
        assertAll(() -> assertEquals("COMPLETED", recorded.get("status")), () -> assertEquals("FAIL", recorded.get("outcome")),
            () -> assertEquals(REVIEWER, number(recorded.get("reviewer_id"))),
            () -> assertTrue(Duration.between(Instant.now().plus(Duration.ofDays(180)), validUntil).abs().toHours() < 24),
            () -> assertTrue(blockers(DECIDER, caseId).contains("CHECK_NOT_PASSED")));
    }

    /** 命中处置：未关闭类追加记录、关闭类关闭命中；TRUE_POSITIVE_REJECT 只能拒绝；重跑取代旧命中。 */
    @Test
    void test_normalFlow_hitResolution() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "TESTHIT");
        screening.mockHit("TESTHIT Primary", "PEP", "PEP-0001", "PEP");
        screening.mockHit("TESTHIT Primary", "SANCTIONS", "SAN-0001", "SANCTIONS");
        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);
        long pep = hitId(caseId, "PEP-0001");
        long sanction = hitId(caseId, "SAN-0001");
        assertTrue(blockers(DECIDER, caseId).contains("SCREENING_HIT_OPEN"));

        resolve(caseId, pep, "POTENTIAL_MATCH").ok();
        assertEquals("OPEN", hit(caseId, pep).get("status"));
        resolve(caseId, pep, "FALSE_POSITIVE").ok();
        resolve(caseId, pep, "NO_MATCH").assertProblem(409, "CLIENT_REVIEW_HIT_CLOSED");
        resolve(caseId, sanction, "TRUE_POSITIVE_REJECT").ok();

        assertAll(() -> assertEquals("RESOLVED", hit(caseId, pep).get("status")),
            () -> assertEquals(2, reviews.resolutions(pep).size()),
            () -> assertEquals("FALSE_POSITIVE", reviews.resolutions(pep).getLast().get("resolution")),
            () -> assertFalse(blockers(DECIDER, caseId).contains("SCREENING_HIT_OPEN")),
            () -> assertTrue(blockers(DECIDER, caseId).contains("SCREENING_TRUE_MATCH")));

        admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/screenings/run", Map.of()).ok();
        reviews.awaitScreeningCompleted(caseId);
        assertAll(() -> assertEquals("SUPERSEDED", hit(caseId, pep).get("status")),
            () -> assertEquals("SUPERSEDED", hit(caseId, sanction).get("status")),
            () -> assertEquals(2, reviews.hits(caseId).stream().filter(row -> "OPEN".equals(row.get("status"))).count()),
            () -> assertTrue(reviews.runs(caseId).stream().anyMatch(run -> "RERUN".equals(run.get("trigger_source")))));
    }

    /** CRR：因子必填且不重复；改评级清空推荐。 */
    @Test
    void test_normalFlow_riskRating() {
        long caseId = register(saOrganization(null));
        admin(REVIEWER, HttpMethod.PUT, "/cases/" + caseId + "/risk-rating", Map.of("riskRating", "LOW", "note", "n",
            "factors", List.of())).assertProblem(422, "VALIDATION_ERROR");
        admin(REVIEWER, HttpMethod.PUT, "/cases/" + caseId + "/risk-rating", Map.of("riskRating", "LOW", "note", "n",
            "factors", List.of(Map.of("factorCode", "PEP", "factorValue", "NO"), Map.of("factorCode", "PEP", "factorValue", "NO"))))
            .assertStatus(400);

        rate(caseId, "MEDIUM").ok();
        recommend(REVIEWER, caseId, "APPROVE").ok();
        rate(caseId, "HIGH").ok();

        Map<String, Object> row = reviews.caseRow(caseId);
        assertAll(() -> assertEquals("HIGH", row.get("risk_rating")), () -> assertNull(row.get("recommendation")),
            () -> assertEquals(2, reviews.riskFactors(caseId).size()),
            () -> assertEquals("HIGH", detail(caseId).path("riskRating").asString()));
    }

    /** 拒绝：四眼需 REJECT 推荐且决定人不同；子案件撤回；没有已批准资格时不投影、不发 QualificationChanged。 */
    @Test
    void test_normalFlow_reject() {
        var builder = saOrganization(null);
        long caseId = register(builder);
        long child = number(reviews.childCases(caseId).getFirst().get("case_id"));
        Map<String, Object> reject = Map.of("reasonCode", "HIGH_RISK", "reason", "internal reason");

        admin(DECIDER, HttpMethod.POST, "/cases/" + caseId + "/rejection", reject).assertProblem(422, "CLIENT_REVIEW_NOT_READY");
        recommend(REVIEWER, caseId, "REJECT").ok();
        admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/rejection", reject).assertProblem(422, "CLIENT_REVIEW_NOT_READY");
        admin(DECIDER, HttpMethod.POST, "/cases/" + caseId + "/rejection", reject).assertCommand(caseId, "REJECTED");
        admin(DECIDER, HttpMethod.POST, "/cases/" + caseId + "/rejection", reject).assertCommand(caseId, "REJECTED");

        assertAll(() -> assertStatus(child, "WITHDRAWN"), () -> assertEquals(1, reviews.decisions(caseId).size()),
            () -> assertNull(reviews.qualification("CLIENT", builder.body().getClientId(), "ORGANIZATION_KYB")),
            () -> assertEquals("REJECTED", awaitEvent("ClientReviewDecided", caseId, "outcome", "REJECTED").path("outcome").asString()));
    }

    /** O14 程序目录：按辖区与状态过滤，含模板与批准信息；非法参数 400。 */
    @Test
    void test_normalFlow_programCatalog() {
        JsonNode sa = adminGet(REVIEWER, "/programs?jurisdictionCode=SA").ok();
        JsonNode first = sa.path("items").path(0);
        assertAll(() -> assertEquals(3, sa.path("items").size()),
            () -> assertTrue(listOf(sa.path("items")).stream().allMatch(item -> "SA".equals(item.path("jurisdictionCode").asString())
                && "ACTIVE".equals(item.path("programStatus").asString()) && "ACTIVE".equals(item.path("versionStatus").asString()))),
            () -> assertEquals(Long.toString(ProgramPackMocker.APPROVER), first.path("approvedBy").asString()),
            () -> assertTrue(first.path("materials").size() > 0),
            () -> assertTrue(first.path("screenings").size() > 0),
            () -> assertTrue(first.path("partyType").isString()),
            () -> assertEquals(9, adminGet(REVIEWER, "/programs").ok().path("items").size()),
            () -> assertEquals(0, adminGet(REVIEWER, "/programs?status=RETIRED").ok().path("items").size()),
            () -> adminGet(REVIEWER, "/programs?jurisdictionCode=sa").assertStatus(400),
            () -> adminGet(REVIEWER, "/programs?tier=STANDARD").assertStatus(400));
    }

    /** 列表按责任 PayFac（0 = 平台自营）与辖区过滤。 */
    @Test
    void test_normalFlow_listFilters() {
        long payfacCase = register(saOrganization(PAYFAC));
        long platformCase = register(saOrganization(null));

        assertAll(() -> assertEquals(List.of(Long.toString(payfacCase)), caseIds("?responsiblePartnerId=" + PAYFAC)),
            () -> assertEquals(List.of(Long.toString(platformCase)), caseIds("?responsiblePartnerId=0")),
            () -> assertEquals(2, caseIds("?jurisdictionCode=SA").size()),
            () -> assertTrue(caseIds("?jurisdictionCode=AE").isEmpty()),
            () -> adminGet(REVIEWER, "/cases?jurisdictionCode=sa").assertStatus(400));
    }

    /** SA 机构 + 一名 UBO，带全部必需材料。 */
    ClientReviewRegistrationRequestBuilder saOrganization(Long partner) {
        var builder = ClientReviewRegistrationRequestBuilder.organization("SA", "LLC").responsiblePartner(partner);
        long ubo = builder.addRelatedPerson("UBO");
        builder.addDocument(builder.primaryPartyId(), "REGISTRATION", "COMMERCIAL_REGISTRATION");
        builder.addDocument(builder.primaryPartyId(), "BANK", "BANK_PROOF");
        builder.addDocument(builder.primaryPartyId(), "ONLINE_PRESENCE", "WEBSITE_SCREENSHOT");
        builder.addDocument(ubo, "IDENTITY", "NATIONAL_ID_CARD");
        mockSubjects(builder, "Org " + builder.body().getApplicationId());
        return builder;
    }

    /** 豁免一项要求。 */
    com.paytech.compliance.test.checker.JsonResponseChecker waive(long caseId, Map<String, Object> requirement) {
        return admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/requirements/" + requirement.get("requirement_id") + "/waiver",
            Map.of("reasonCode", "SINGLE_SHAREHOLDER", "note", "one shareholder"));
    }

    /** 处置命中。 */
    com.paytech.compliance.test.checker.JsonResponseChecker resolve(long caseId, long hitId, String resolution) {
        return admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/screening-hits/" + hitId + "/resolution",
            Map.of("resolution", resolution, "reasonCode", "REVIEWED", "note", "checked"));
    }

    /** 要求行。 */
    Map<String, Object> requirement(long caseId, String code) {
        return reviews.requirements(caseId).stream().filter(row -> code.equals(row.get("requirement_code"))).findFirst().orElseThrow();
    }

    /** 检查行。 */
    Map<String, Object> check(long caseId, String code) {
        return reviews.checks(caseId).stream().filter(row -> code.equals(row.get("check_code"))).findFirst().orElseThrow();
    }

    /** 命中 ID。 */
    long hitId(long caseId, String ref) {
        return number(reviews.hits(caseId).stream().filter(row -> ref.equals(row.get("external_hit_ref"))).findFirst().orElseThrow()
            .get("hit_id"));
    }

    /** 命中行。 */
    Map<String, Object> hit(long caseId, long hitId) {
        return reviews.hits(caseId).stream().filter(row -> number(row.get("hit_id")) == hitId).findFirst().orElseThrow();
    }

    /** 列表中的案件 ID。 */
    List<String> caseIds(String query) {
        List<String> ids = new java.util.ArrayList<>();
        adminGet(REVIEWER, "/cases" + query).ok().path("items").forEach(item -> ids.add(item.path("caseId").asString()));
        return ids;
    }
}
