package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.builder.ProgramBuilder;
import com.paytech.compliance.test.mapper.ProgramTestMapper;
import com.paytech.compliance.test.mocker.ProgramPackMocker.MockedProgram;

import tools.jackson.databind.JsonNode;

/**
 * 案件工作台详情、I02 分类与程序版本冻结（AC-06 展示、AC-12）
 * <li>详情含主体材料（允许类型、可豁免、证据文件地址）、检查、筛查执行与命中、评级因子、时间线与门禁；门禁按查看人计算四眼。</li>
 * <li>I02 同集合重放成功、不同集合 409；在途案件保持立案时的程序版本，新版本只影响之后的立案。</li>
 */
class ClientReviewCaseDetailApiAutoTest extends ClientReviewFlowTestBase {
    @org.springframework.beans.factory.annotation.Autowired
    private ProgramTestMapper programMapper;

    /** 筛查无命中。 */
    @BeforeEach
    void setUpDetail() {
        screening.mockNoHitsByDefault();
    }

    /** 详情结构与按查看人的四眼门禁。 */
    @Test
    void test_normalFlow_detail() {
        programs.mockCountryPacks();
        var builder = ClientReviewRegistrationRequestBuilder.organization("AE", "LLC").responsiblePartner(PAYFAC);
        long director = builder.addRelatedPerson("DIRECTOR");
        String license = builder.addDocument(builder.primaryPartyId(), "REGISTRATION", "TRADE_LICENSE");
        mockSubjects(builder, "Detail LLC");
        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);
        rate(caseId, "MEDIUM").ok();
        recommend(REVIEWER, caseId, "APPROVE").ok();

        JsonNode body = detail(caseId);
        JsonNode main = body.path("subjects").path(0);
        JsonNode registration = find(main.path("requirements"), "requirementCode", "REGISTRATION");
        assertAll(() -> assertEquals("AE", body.path("jurisdictionCode").asString()),
            () -> assertEquals(Long.toString(PAYFAC), body.path("responsiblePartnerId").asString()),
            () -> assertEquals("MEDIUM", body.path("riskRating").asString()),
            () -> assertEquals(2, body.path("riskFactors").size()),
            () -> assertEquals("APPROVE", body.path("recommendation").asString()),
            () -> assertEquals(Long.toString(REVIEWER), body.path("recommendedBy").asString()),
            () -> assertTrue(body.path("fourEyesRequired").asBoolean()),
            () -> assertEquals(2, body.path("subjects").size()),
            () -> assertEquals(List.of("TRADE_LICENSE"), List.of(registration.path("documentTypes").path(0).asString())),
            () -> assertEquals(license, registration.path("evidence").path(0).path("fileAddress").asString()),
            () -> assertEquals(3, main.path("checks").size()),
            () -> assertEquals(3, main.path("screenings").size()),
            () -> assertEquals("SUCCEEDED", main.path("screenings").path(0).path("status").asString()),
            () -> assertEquals("DIRECTOR_KYC", body.path("subjects").path(1).path("dependencyTypes").path(0).asString()),
            () -> assertEquals(Long.toString(director), body.path("subjects").path(1).path("partyId").asString()),
            () -> assertEquals("REGISTER", body.path("timeline").path(0).path("actionType").asString()),
            () -> assertTrue(listOf(body.path("timeline")).stream().anyMatch(item -> "START_REVIEW".equals(item.path("actionType")
                .asString()))));
        assertTrue(blockers(REVIEWER, caseId).contains("FOUR_EYES_SAME_PERSON"));
        assertTrue(!blockers(DECIDER, caseId).contains("FOUR_EYES_SAME_PERSON"));
    }

    /** I02：同集合重放成功；不同集合 409。 */
    @Test
    void test_edgeCase_classificationReplay() {
        programs.mockCountryPacks();
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Classified");
        long caseId = register(builder);

        classify(caseId, builder.build()).ok();
        classify(caseId, builder.build()).ok();
        classify(caseId, builder.build(), "TRAVEL").assertProblem(409, "CLIENT_REVIEW_CLASSIFICATION_CONFLICT");
        assertEquals(1, detail(caseId).path("classifications").size());
        assertTrue(!blockers(DECIDER, caseId).contains("CLASSIFICATION_NOT_RECEIVED"));
    }

    /** 程序发布新版本后，在途案件仍按立案时的版本；新立案使用新版本。 */
    @Test
    void test_edgeCase_versionFrozen() {
        MockedProgram v1 = programs.mock(ProgramBuilder.zzOrganization().code("ZZ_INDIVIDUAL").scope("INDIVIDUAL", "CLIENT",
            "INDIVIDUAL_KYC"));
        var first = ClientReviewRegistrationRequestBuilder.individual("ZZ");
        mockSubjects(first, "First");
        long firstCase = register(first);

        programMapper.retireVersion(v1.versionId());
        MockedProgram v2 = programs.mockVersion(v1.programId(), 2, ProgramBuilder.zzOrganization().code("ZZ_INDIVIDUAL")
            .scope("INDIVIDUAL", "CLIENT", "INDIVIDUAL_KYC").material(new ProgramBuilder.Material("TAX", List.of("TAX_CARD"), true, null,
                false)));
        var second = ClientReviewRegistrationRequestBuilder.individual("ZZ");
        mockSubjects(second, "Second");
        long secondCase = register(second);

        Map<String, Object> firstRow = reviews.caseRow(firstCase);
        assertAll(() -> assertEquals(v1.versionId(), number(firstRow.get("dd_program_version_id"))),
            () -> assertEquals(2, reviews.requirements(firstCase).size()),
            () -> assertEquals(2, detail(firstCase).path("subjects").path(0).path("requirements").size()),
            () -> assertEquals(v2.versionId(), number(reviews.caseRow(secondCase).get("dd_program_version_id"))),
            () -> assertEquals(3, reviews.requirements(secondCase).size()));
    }

    /** 数组中按字段值查找元素。 */
    static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asString())) {
                return item;
            }
        }
        throw new AssertionError("未找到 " + field + "=" + value);
    }
}
