package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

/**
 * 2026-10-27 之前的遗留申请（发布回填后：辖区 LEGACY、schema_id 为空、证件已脱敏）在 Client 通道只读可用：
 * 申请资料与运营详情照常读取；需求清单为空（ready=false）；表单规格与提交核对没有 Schema 可依，返回 409 APPLICATION_NOT_EDITABLE，都不是 500。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi#requirements(String, String, String, String)
 */
class LegacyApplicationReadTest extends AbstractOnboardingHttpTest {

    /** 遗留的已批准个人申请。 */
    @Test
    void test_legacyApplicationReads() {
        var app = applications.approved(ACCOUNT_ID, false);
        mapper.legacyShape(app.applicationId(), app.clientId(), app.partyId(), app.profileId());
        mapper.contactTokens(app.profileId()).forEach((type, token) -> vault.mockReveal(token, "EMAIL".equals(type)
            ? "legacy@example.test" : "+971500000000"));

        var read = draft(app.applicationId());
        assertEquals("LEGACY", read.path("jurisdictionCode").asString());
        assertTrue(read.path("schemaId").isNull());
        assertEquals("****1234", read.path("values").path("identifier").path("identifierMasked").asString());
        assertEquals("legacy@example.test", read.path("values").path("contact").path("email").asString());
        assertTrue(vault.calls().isEmpty(), "读取不 Protect");

        var schema = formSchema("applicationId", Long.toString(app.applicationId()));
        assertEquals(409, schema.getStatus().value(), new String(schema.getResponseBody()));
        assertEquals("APPLICATION_NOT_EDITABLE", body(schema).path("code").asString());
        var requirements = requirements(app.applicationId());
        assertEquals(200, requirements.getStatus().value(), new String(requirements.getResponseBody()));
        assertFalse(body(requirements).path("ready").asBoolean());
        assertEquals(0, body(requirements).path("items").size());
        assertEquals("APPROVED", body(requirements).path("status").asString());
        var check = http.get().uri("/client-onboarding/v1/applications/{id}/submission-check", app.applicationId()).headers(this::client)
            .exchange().expectBody(byte[].class).returnResult();
        assertEquals(409, check.getStatus().value(), new String(check.getResponseBody()));
        var detail = http.get().uri("/client-master-admin/v1/clients/{id}", app.clientId()).headers(headers -> {
            headers.set("X-Gateway-Actor-Subject", "9300000009");
            headers.set("X-Gateway-Session-Id", "ops-session-1");
        }).exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, detail.getStatus().value(), new String(detail.getResponseBody()));
        assertTrue(body(detail).path("applications").get(0).path("schemaId").isNull());
        assertEquals(0, owners.count("program:"));
        assertEquals(0, owners.count("file:"));
    }
}
