package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.builder.ProgramBuilder;
import com.paytech.compliance.test.checker.JsonResponseChecker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 虚构国家 ZZ 国家包全链（AC-11 compliance 部分、technique S-17 主门禁）
 * <li>只经测试夹具（等价于 DML）新增 ZZ 程序：自定义材料类型 ZZ_BUSINESS_PERMIT、ZZ_RESIDENT_CARD 与人工检查 ZZ_REGISTRY_CHECK，
 * 机构程序关闭四眼；不改任何代码跑通 解析 → 立案 → 筛查 → 审核 → 批准 → 事件。</li>
 * <li>关闭四眼的程序：无推荐也可批准，决定记 four_eyes_waived=1。</li>
 */
class ZzCountryPackAutoTest extends ClientReviewFlowTestBase {

    /** ZZ 程序与无命中筛查。 */
    @BeforeEach
    void setUpZz() {
        programs.mock(ProgramBuilder.zzOrganization().fourEyes(false).reviewIntervalMonths(12));
        programs.mock(ProgramBuilder.zzRelatedPerson());
        screening.mockNoHitsByDefault();
    }

    /** ZZ 全链。 */
    @Test
    void test_normalFlow_zzFullChain() {
        assertEquals("ZZ_CLIENT_ORGANIZATION_STANDARD", resolve("ORGANIZATION", "CLIENT").ok().path("programCode").asString());
        var builder = ClientReviewRegistrationRequestBuilder.organization("ZZ", "ZZ_COOPERATIVE");
        long member = builder.addRelatedPerson("ZZ_MANAGING_MEMBER");
        builder.addDocument(builder.primaryPartyId(), "REGISTRATION", "ZZ_BUSINESS_PERMIT");
        builder.addDocument(member, "IDENTITY", "ZZ_RESIDENT_CARD");
        mockSubjects(builder, "Zed Cooperative");

        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);
        prepareForDecision(caseId, builder.build());
        assertEquals(List.of(), blockers(DECIDER, caseId));
        approve(DECIDER, caseId, null).assertCommand(caseId, "APPROVED");

        Map<String, Object> decision = reviews.decisions(caseId).getFirst();
        Map<String, Object> qualification = reviews.qualification("CLIENT", builder.body().getClientId(), "ORGANIZATION_KYB");
        assertAll(() -> assertEquals("ZZ", reviews.caseRow(caseId).get("jurisdiction_code")),
            () -> assertEquals("RELATED_PERSON_KYC", reviews.dependencies(caseId).getFirst().get("dependency_type")),
            () -> assertEquals("ZZ_REGISTRY_CHECK", reviews.checks(caseId).getFirst().get("check_code")),
            () -> assertEquals(Boolean.TRUE, decision.get("four_eyes_waived")),
            () -> assertEquals("ZZ", decision.get("jurisdiction_code")),
            () -> assertEquals(12, ((Number)qualification.get("review_interval_months")).intValue()),
            () -> assertEquals("APPROVED", awaitEvent("ClientReviewDecided", caseId, "outcome", "APPROVED").path("outcome").asString()),
            () -> assertEquals("ZZ", awaitEvent("QualificationChanged", number(qualification.get("qualification_id")), "status",
                "APPROVED").path("jurisdictionCode").asString()));
    }

    /** 解析 ZZ 程序。 */
    JsonResponseChecker resolve(String partyType, String roleType) {
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.get("/compliance/v1/programs/resolve?jurisdiction=ZZ&partyType=" + partyType + "&roleType="
            + roleType, headers), objectMapper, requestId);
    }
}
