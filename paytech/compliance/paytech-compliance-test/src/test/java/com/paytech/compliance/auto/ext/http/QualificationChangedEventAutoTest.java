package com.paytech.compliance.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.compliance.test.base.ComplianceMockTestBase;
import com.paytech.compliance.test.builder.QualificationManualDecisionRequestBuilder;
import com.paytech.compliance.test.builder.QualificationRevocationRequestBuilder;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 运营手工登记与撤销资格同事务登记 QUALIFICATION_CHANGED，提交后发布 QualificationChanged v1（AC-09 compliance 部分）
 * <li>撤销发布 status=REVOKED、previousStatus=APPROVED、decisionSource=MANUAL，client-master 据此挂起 Client。</li>
 */
class QualificationChangedEventAutoTest extends ComplianceMockTestBase {

    /** 登记版本 1 APPROVED，撤销版本 2 REVOKED，各发布一条事件。 */
    @Test
    void test_normalFlow_manualAndRevoke() {
        String roleId = Long.toString(800000000000000L + (System.nanoTime() % 1_000_000L));
        JsonNode created = post("/compliance-admin/v1/qualifications/manual-decisions",
            QualificationManualDecisionRequestBuilder.template().roleId(roleId).build());
        String qualificationId = created.path("qualificationId").asString();

        post("/compliance-admin/v1/qualifications/" + qualificationId + "/revocation",
            QualificationRevocationRequestBuilder.template(1L).build());

        JsonNode approved = events.await("QualificationChanged", qualificationId, e -> "APPROVED".equals(e.path("payload")
            .path("status").asString())).path("payload");
        JsonNode revoked = events.await("QualificationChanged", qualificationId, e -> "REVOKED".equals(e.path("payload")
            .path("status").asString()));
        assertAll(() -> assertEquals(1L, approved.path("qualificationVersion").asLong()),
            () -> assertTrue(approved.path("previousStatus").isNull()),
            () -> assertEquals("MANUAL", approved.path("decisionSource").asString()),
            () -> assertEquals(roleId, approved.path("roleId").asString()),
            () -> assertEquals(2L, revoked.path("aggregateVersion").asLong()),
            () -> assertEquals("APPROVED", revoked.path("payload").path("previousStatus").asString()),
            () -> assertEquals("ROLE_QUALIFICATION", revoked.path("aggregateType").asString()),
            () -> assertTrue(revoked.path("payload").path("caseId").isNull()));
    }

    /** 运营资格接口调用。 */
    JsonNode post(String path, Object body) {
        String requestId = "compliance-qualification-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "600000000000009");
        headers.set("X-Gateway-Session-Id", "session-qualification");
        var result = http.post(path, headers, body);
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }
}
