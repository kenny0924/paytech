package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.checker.JsonResponseChecker;
import com.paytech.compliance.test.mocker.LegacyCaseMocker;
import com.paytech.compliance.test.mocker.LegacyCaseMocker.LegacyCase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 迁移后旧案件兼容（发布决策 P-8）
 * <li>保留的已批准旧案件（RETIRED 程序、辖区 LEGACY、无评级与推荐）在工作台详情、列表与客户结果中照常可读。</li>
 * <li>RETIRED 旧程序不再被解析，但仍出现在运营程序目录中。</li>
 */
class LegacyCaseCompatibilityAutoTest extends ClientReviewFlowTestBase {
    @Autowired
    private LegacyCaseMocker legacyCases;

    /** 详情、列表、客户结果均可读，旧程序不参与解析。 */
    @Test
    void test_edgeCase_approvedLegacyCaseReadable() {
        LegacyCase legacy = legacyCases.mockApprovedOrganization();

        JsonNode body = detail(legacy.caseId());
        JsonNode registration = ClientReviewCaseDetailApiAutoTest.find(body.path("subjects").path(0).path("requirements"),
            "requirementCode", "REGISTRATION");
        JsonNode result = result(Long.toString(legacy.applicationId())).ok();
        assertAll(() -> assertEquals("APPROVED", body.path("status").asString()),
            () -> assertEquals(LegacyCaseMocker.LEGACY_JURISDICTION, body.path("jurisdictionCode").asString()),
            () -> assertTrue(body.path("riskRating").isNull() || body.path("riskRating").isMissingNode()),
            () -> assertEquals("REGISTRATION_CERTIFICATE", registration.path("documentTypes").path(0).asString()),
            () -> assertEquals(legacy.fileAddress(), registration.path("evidence").path(0).path("fileAddress").asString()),
            () -> assertEquals("APPROVE", body.path("timeline").path(0).path("actionType").asString()),
            () -> assertEquals(Long.toString(legacy.caseId()), adminGet(REVIEWER, "/cases?jurisdictionCode="
                + LegacyCaseMocker.LEGACY_JURISDICTION).ok().path("items").path(0).path("caseId").asString()),
            () -> assertEquals("APPROVED", result.path("outcome").asString()),
            () -> assertEquals(1, result.path("roundNo").asInt()),
            () -> assertEquals("RETIRED", adminGet(REVIEWER, "/programs?status=RETIRED").ok().path("items").path(0)
                .path("versionStatus").asString()));
        resolveLegacyScope().assertStatus(503);
    }

    /** 以旧全局程序的范围（无辖区限制）解析：只剩 RETIRED 旧程序时应不可用。 */
    private JsonResponseChecker resolveLegacyScope() {
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.get("/compliance/v1/programs/resolve?jurisdiction=ZZ&partyType=ORGANIZATION&roleType=CLIENT"
            + "&tier=STANDARD", headers), objectMapper, requestId);
    }
}
