package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

/**
 * 入驻码（AC-04）：Operations 为具备 OE_PROVIDER 角色的 Local Partner 签发、查询与作废；Client 开始申请时凭码冻结责任 PayFac，
 * compliance 程序按责任方解析；码无效、用尽或作废一律 ONBOARDING_CODE_INVALID。
 *
 * @see com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi#issueOnboardingCode
 * @see com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi#revokeOnboardingCode
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#create
 */
class OnboardingCodeTest extends AbstractOnboardingHttpTest {
    private static final long OPERATOR_ID = 9_300_000_009L;

    private long partnerId;

    /** 一个具备 OE_PROVIDER 角色的 Partner 与一个无角色 Partner；程序材料。 */
    @BeforeEach
    void setUpPartners() {
        partnerId = applications.nextId();
        mapper.insertLocalPartner(partnerId, applications.nextId(), "OE_PROVIDER", applications.nextId(), now());
        owners.mockStandardPrograms();
    }

    /** 签发 → 凭码开始申请冻结责任方并计数 → 用尽后拒绝；作废的码同样拒绝。 */
    @Test
    void test_issueUseAndRevoke() {
        var issued = issue(partnerId, Map.of("maxUses", 1, "note", "Launch partner"));
        assertEquals(201, issued.getStatus().value(), new String(issued.getResponseBody()));
        var code = body(issued);
        assertTrue(code.path("code").asString().matches("[A-Z0-9]{4}-[A-Z0-9]{4}"), code.toString());
        assertEquals("ACTIVE", code.path("status").asString());
        assertEquals(Long.toString(OPERATOR_ID), code.path("issuedBy").asString());

        long applicationId = started("ORGANIZATION", "AE", "LLC", code.path("code").asString().toLowerCase());

        var terms = mapper.applicationTerms(applicationId);
        assertEquals(Long.parseLong(code.path("codeId").asString()), ((Number)terms.get("onboarding_code_id")).longValue());
        assertEquals(partnerId, ((Number)terms.get("responsible_partner_id")).longValue());
        assertEquals(1, ((Number)mapper.onboardingCode(Long.parseLong(code.path("codeId").asString())).get("use_count")).intValue());
        assertEquals(200, formSchema("applicationId", Long.toString(applicationId)).getStatus().value());
        assertTrue(owners.programCalls().stream().allMatch(call -> Long.toString(partnerId).equals(call.responsiblePartnerId())));
        var listed = body(list(partnerId)).get(0);
        assertEquals(1, listed.path("useCount").asInt());
        assertTrue(listed.path("code").isNull() || listed.path("code").isMissingNode(), "列表不返回明文码");
        assertEquals("****-" + code.path("code").asString().substring(5), listed.path("codeMasked").asString());
        var read = draft(applicationId);
        assertEquals(Long.toString(partnerId), read.path("responsiblePartnerId").asString());
        assertEquals("Gulf Pay LLC", read.path("responsiblePartnerName").asString());

        var exhausted = start("ORGANIZATION", "AE", "LLC", code.path("code").asString());
        assertEquals(422, exhausted.getStatus().value());
        assertEquals("ONBOARDING_CODE_INVALID", body(exhausted).path("code").asString());

        var second = body(issue(partnerId, Map.of("note", "Revoked")));
        var revoked = http.post().uri("/client-master-admin/v1/local-partners/{p}/onboarding-codes/{c}/revocation", partnerId,
            second.path("codeId").asString()).headers(this::operator).exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, revoked.getStatus().value(), new String(revoked.getResponseBody()));
        assertEquals("REVOKED", body(revoked).path("status").asString());
        assertEquals("ONBOARDING_CODE_INVALID", body(start("ORGANIZATION", "AE", "LLC", second.path("code").asString())).path("code")
            .asString());
        assertEquals("ONBOARDING_CODE_INVALID", body(start("ORGANIZATION", "AE", "LLC", "ZZZZ-0000")).path("code").asString());
        assertEquals(2, body(list(partnerId)).size());
    }

    /** 不具备 OE_PROVIDER 角色的 Partner 不能签发；不存在的 Partner 404；未到生效期的码不可用。 */
    @Test
    void test_issueRules() {
        long plain = applications.nextId();
        mapper.insertLocalPartner(plain, applications.nextId(), null, 0, now());

        var rejected = issue(plain, Map.of());
        assertEquals(422, rejected.getStatus().value());
        assertEquals("PARTNER_NOT_OE_PROVIDER", body(rejected).path("code").asString());
        assertEquals(404, issue(applications.nextId(), Map.of()).getStatus().value());

        var future = body(issue(partnerId, Map.of("effectiveFrom", Instant.now().plus(1, ChronoUnit.DAYS).toString())));
        assertEquals("ONBOARDING_CODE_INVALID", body(start("INDIVIDUAL", "AE", null, future.path("code").asString())).path("code")
            .asString());
    }

    /** 签发入驻码。 */
    EntityExchangeResult<byte[]> issue(long localPartnerId, Map<String, Object> body) {
        return http.post().uri("/client-master-admin/v1/local-partners/{p}/onboarding-codes", localPartnerId).headers(this::operator)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(body)).exchange().expectBody(byte[].class)
            .returnResult();
    }

    /** 查询入驻码。 */
    EntityExchangeResult<byte[]> list(long localPartnerId) {
        return http.get().uri("/client-master-admin/v1/local-partners/{p}/onboarding-codes", localPartnerId).headers(this::operator)
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** Gateway 可信 Operations 头。 */
    void operator(HttpHeaders headers) {
        headers.set("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID));
        headers.set("X-Gateway-Session-Id", "ops-session-1");
        headers.set("X-Request-Id", "cct-onboarding-code-test");
    }

    /** UTC 微秒。 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
