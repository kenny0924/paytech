package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.paytech.compliance.api.ClientReviewRegistrationApi;
import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.mocker.external.ScreeningMocker;

/**
 * I01 v2 登记与 CASE_SCREENING 筛查、I03 撤回（AC-05、AC-13、AC-10 责任方冻结）
 * <li>机构申请建主案件 + 每名关联人员子案件 + 依赖；程序按（辖区，主体类型，角色，档次）解析并冻结；检查按模板实例化为待录入。</li>
 * <li>立案后事务外经 client-master 读取名称、经 channel-connector Mock 渠道筛查：每主体每类型一次执行，命中落 hit / hit_list。</li>
 * <li>重放同内容返回原案件，改内容 409；材料不在槽或类型不允许 400；无程序 503；已有案件却缺少 previousCaseId 400。</li>
 *
 * @see ClientReviewRegistrationApi#register(String, com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody)
 */
class ClientReviewRegistrationApiAutoTest extends ClientReviewFlowTestBase {

    /** 本类场景都使用初始化 DML 的三国程序，筛查默认无命中。 */
    @BeforeEach
    void setUpRegistration() {
        programs.mockCountryPacks();
        screening.mockNoHitsByDefault();
    }

    /** SA 机构 + 两名关联人员：主案件、子案件、依赖、冻结字段、要求 / 证据 / 检查与 9 次筛查。 */
    @Test
    void test_normalFlow_organization() {
        var builder = ClientReviewRegistrationRequestBuilder.organization("SA", "LLC").responsiblePartner(PAYFAC);
        long ubo = builder.addRelatedPerson("UBO");
        long signatory = builder.addRelatedPerson("SIGNATORY");
        String registration = builder.addDocument(builder.primaryPartyId(), "REGISTRATION", "COMMERCIAL_REGISTRATION");
        builder.addDocument(ubo, "IDENTITY", "NATIONAL_ID_CARD");
        mockSubjects(builder, "Acme Trading LLC");

        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);

        Map<String, Object> main = reviews.caseRow(caseId);
        List<Map<String, Object>> children = reviews.childCases(caseId);
        assertAll(() -> assertEquals("SUBMITTED", main.get("status")),
            () -> assertEquals("ONBOARDING", main.get("case_type")),
            () -> assertEquals("SA", main.get("jurisdiction_code")),
            () -> assertEquals("STANDARD", main.get("tier")),
            () -> assertEquals(1, ((Number)main.get("round_no")).intValue()),
            () -> assertEquals(PAYFAC, number(main.get("responsible_partner_id"))),
            () -> assertEquals(2, children.size()),
            () -> assertEquals(Set.of("UBO_KYC", "SIGNATORY_KYC"), reviews.dependencies(caseId).stream()
                .map(row -> (String)row.get("dependency_type")).collect(Collectors.toSet())),
            () -> assertEquals(8, reviews.requirements(caseId).size()),
            () -> assertEquals("RECEIVED", requirementStatus(caseId, "REGISTRATION")),
            () -> assertEquals("PENDING", requirementStatus(caseId, "BANK")),
            () -> assertEquals(registration, reviews.evidence(caseId).getFirst().get("file_address")),
            () -> assertEquals("COMMERCIAL_REGISTRATION", reviews.evidence(caseId).getFirst().get("document_type")),
            () -> assertEquals(Set.of("WEBSITE_REVIEW", "TERMINATED_MERCHANT_LOOKUP", "MANUAL_SCREENING"), reviews.checks(caseId).stream()
                .map(row -> (String)row.get("check_code")).collect(Collectors.toSet())),
            () -> reviews.checks(caseId).forEach(row -> assertEquals("REQUESTED", row.get("status"))),
            () -> assertEquals("OWNERSHIP", reviews.subject(childOf(caseId, ubo)).get("ubo_basis")),
            () -> assertEquals(9, screening.calls().size()),
            () -> assertEquals(3, reviews.runs(caseId).stream().filter(run -> "SUCCEEDED".equals(run.get("status"))).count()),
            () -> assertEquals(3, reviews.runs(childOf(caseId, signatory)).size()));
        ScreeningMocker.Call orgCall = screening.callsFor("Acme Trading LLC").getFirst();
        assertAll(() -> assertEquals("S_SCREENING_MOCK", orgCall.channelCode()),
            () -> assertEquals("SCREENING_SEARCH", orgCall.standardApiCode()),
            () -> assertEquals("compliance-service", orgCall.caller()),
            () -> assertEquals("ORGANIZATION", orgCall.subjectType()),
            () -> assertNull(orgCall.birthYear()),
            () -> assertEquals(List.of("SA"), orgCall.countries()),
            () -> assertEquals("1990", screening.callsFor("Person1 Related").getFirst().birthYear()),
            () -> assertEquals(List.of("SA", "AE"), screening.callsFor("Person1 Related").getFirst().countries()),
            () -> assertTrue(partyProfiles.callers().stream().allMatch("compliance-service"::equals)));
    }

    /** 名称含命中配置时落命中、名单类型与 OPEN 状态。 */
    @Test
    void test_normalFlow_screeningHit() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("AE");
        mockSubjects(builder, "TESTHIT");
        screening.mockHit("TESTHIT Primary", "PEP", "PEP-0001", "PEP");

        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);

        List<Map<String, Object>> hits = reviews.hits(caseId);
        assertAll(() -> assertEquals(1, hits.size()),
            () -> assertEquals("OPEN", hits.getFirst().get("status")),
            () -> assertEquals("PEP-0001", hits.getFirst().get("external_hit_ref")),
            () -> assertEquals("HIGH", hits.getFirst().get("risk_level")),
            () -> assertEquals(List.of("PEP"), reviews.hitLists(number(hits.getFirst().get("hit_id")))));
    }

    /** 渠道失败 / 结果未知：执行记为 FAILED / TIMED_OUT，任务保留重试，不虚构成功。 */
    @Test
    void test_businessRuleViolation_screeningUnavailable() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("EG");
        mockSubjects(builder, "Unknown");
        screening.mockDefaultFault(ScreeningMocker.problem(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_BUDGET_EXHAUSTED"));

        long caseId = register(builder);

        List<Map<String, Object>> events = reviews.events("CASE_SCREENING", Long.toString(caseId));
        assertAll(() -> assertEquals(1, events.size()),
            () -> assertTrue(Set.of("INIT", "ERROR").contains(events.getFirst().get("execute_status"))),
            () -> assertTrue(reviews.runs(caseId).stream().noneMatch(run -> "SUCCEEDED".equals(run.get("status")))),
            () -> assertTrue(blockers(DECIDER, caseId).contains("SCREENING_INCOMPLETE")));
    }

    /** 同提交编号同内容重放返回原案件；改内容 409。 */
    @Test
    void test_edgeCase_replay() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        builder.addDocument(builder.primaryPartyId(), "IDENTITY", "IQAMA");
        mockSubjects(builder, "Replay");
        long caseId = register(builder);

        assertEquals(Long.toString(caseId), submit(builder.build()).ok().path("caseId").asString());
        builder.addDocument(builder.primaryPartyId(), "BANK", "BANK_PROOF");
        submit(builder.build()).assertProblem(409, "CONCURRENT_REQUEST");
    }

    /** 材料不在程序材料槽或类型不在允许范围：400，不建案。 */
    @Test
    void test_businessRuleViolation_material() {
        var unknownSlot = ClientReviewRegistrationRequestBuilder.individual("SA");
        unknownSlot.addDocument(unknownSlot.primaryPartyId(), "CONSTITUTION", "MOA");
        submit(unknownSlot.build()).assertProblem(400, "BUSINESS_CHECK_FAILED");

        var wrongType = ClientReviewRegistrationRequestBuilder.individual("SA");
        wrongType.addDocument(wrongType.primaryPartyId(), "IDENTITY", "EMIRATES_ID");
        submit(wrongType.build()).assertProblem(400, "BUSINESS_CHECK_FAILED");
        assertTrue(reviews.casesOfApplication(Long.parseLong(wrongType.body().getApplicationId())).isEmpty());
    }

    /** 辖区没有程序：503，不建案、不回退。 */
    @Test
    void test_noDataFound_program() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("ZZ");

        submit(builder.build()).assertProblem(503, "SERVICE_UNAVAILABLE");
        assertTrue(reviews.casesOfApplication(Long.parseLong(builder.body().getApplicationId())).isEmpty());
    }

    /** 申请已有案件时首次提交身份（缺 previousCaseId）被拒；previousCaseId 指向进行中案件被拒。 */
    @Test
    void test_businessRuleViolation_previousCase() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Twice");
        long caseId = register(builder);

        builder.body().setSourceSubmissionId(java.util.UUID.randomUUID().toString());
        submit(builder.build()).assertProblem(400, "BUSINESS_CHECK_FAILED");
        submit(builder.resubmit(caseId).build()).assertProblem(400, "BUSINESS_CHECK_FAILED");
    }

    /** 请求体合同：文件地址与辖区格式不合法时 422 字段校验失败。 */
    @Test
    void test_invalidParameter() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        builder.addDocument(builder.primaryPartyId(), "IDENTITY", "IQAMA");
        builder.body().getDocuments().getFirst().setFileAddress("/files/v2/1");
        submit(builder.build()).assertProblem(422, "VALIDATION_ERROR");

        var lower = ClientReviewRegistrationRequestBuilder.individual("sa");
        submit(lower.build()).assertProblem(422, "VALIDATION_ERROR");
    }

    /** I03 撤回进行中案件：主 / 子案件 WITHDRAWN，幂等；不发布审核结果；申请不符 404。 */
    @Test
    void test_normalFlow_withdrawal() {
        var builder = ClientReviewRegistrationRequestBuilder.organization("AE", "LLC");
        long person = builder.addRelatedPerson("DIRECTOR");
        mockSubjects(builder, "Withdraw Co");
        long caseId = register(builder);

        withdraw(caseId, "123456789").assertProblem(404, "CLIENT_REVIEW_NOT_FOUND");
        withdraw(caseId, builder.body().getApplicationId()).assertCommand(caseId, "WITHDRAWN");
        withdraw(caseId, builder.body().getApplicationId()).assertCommand(caseId, "WITHDRAWN");

        assertAll(() -> assertStatus(caseId, "WITHDRAWN"),
            () -> assertStatus(childOf(caseId, person), "WITHDRAWN"),
            () -> assertNull(reviews.caseRow(caseId).get("active_slot")),
            () -> assertEquals("WITHDRAW", reviews.stateChanges(caseId).getLast().get("action_type")),
            () -> assertTrue(reviews.events("CLIENT_REVIEW_RESULT", Long.toString(caseId)).isEmpty()),
            () -> assertEquals("WITHDRAWN", result(builder.body().getApplicationId()).ok().path("outcome").asString()));
    }

    /** 已批准案件不能撤回。 */
    @Test
    void test_businessRuleViolation_withdrawClosed() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("AE");
        builder.addDocument(builder.primaryPartyId(), "IDENTITY", "PASSPORT");
        builder.addDocument(builder.primaryPartyId(), "REGISTRATION", "TRADE_LICENSE");
        builder.addDocument(builder.primaryPartyId(), "BANK", "BANK_PROOF");
        builder.addDocument(builder.primaryPartyId(), "ONLINE_PRESENCE", "WEBSITE_SCREENSHOT");
        mockSubjects(builder, "Closed");
        long caseId = register(builder);
        prepareForDecision(caseId, builder.build());
        recommend(REVIEWER, caseId, "APPROVE").ok();
        approve(DECIDER, caseId, null).assertCommand(caseId, "APPROVED");

        withdraw(caseId, builder.body().getApplicationId()).assertProblem(409, "CLIENT_REVIEW_CLOSED");
    }

    /** 案件材料要求状态。 */
    String requirementStatus(long caseId, String code) {
        return reviews.requirements(caseId).stream().filter(row -> code.equals(row.get("requirement_code"))).findFirst().orElseThrow()
            .get("status").toString();
    }

    /** 关联人员的子案件 ID。 */
    long childOf(long caseId, long partyId) {
        return reviews.childCases(caseId).stream().filter(row -> number(row.get("role_id")) == partyId).findFirst()
            .map(row -> number(row.get("case_id"))).orElseThrow();
    }
}
