package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ReviewFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;
import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker.Material;

import tools.jackson.databind.json.JsonMapper;

/**
 * 退回后修改证件再提交：C14 提交 → 审核退回 → C04 改证件号（Vault Protect 得新 Token）→ C14 重提，按新 Token 绑定新身份键，旧键保留原归属。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 */
class ResubmitAfterReturnTest extends AbstractClientReviewTest {
    private static final String HASH = "a".repeat(64);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 个人身份证明为必需材料；结果分区队列由基类清空。 */
    @BeforeEach
    void setUpReturnFlow() {
        owners.mockProgram("INDIVIDUAL", "CLIENT", new Material("IDENTITY", "IDENTITY_DOCUMENT", true));
    }

    /** 退回后改证件号：保存成功并解绑，重提绑定新键，旧键仍归本主体。 */
    @Test
    void test_changeIdentifierAfterReturn() throws Exception {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");
        applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", fileId, HASH);
        var review = submitAndWaitUnderReview(app, applications.nextId());
        Object oldKeyId = mapper.identifier(app.profileId()).get("identity_key_id");

        publishResult(review, review.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(app.applicationId()).get("status")), Duration.ofSeconds(10));

        vault.mockProtect();
        var saved = saveIdentifierValue(app, "n99-" + app.partyId() % 1_000_000);

        assertEquals(200, saved.getStatus().value(), new String(saved.getResponseBody()));
        var identifier = mapper.identifier(app.profileId());
        String normalized = "N99" + app.partyId() % 1_000_000;
        assertNull(identifier.get("identity_key_id"));
        assertEquals(OnboardingFixtures.passportToken(normalized), identifier.get("identifier_vault_token"));
        assertEquals(OnboardingFixtures.masked(normalized), identifier.get("identifier_masked_value"));
        assertNull(identifier.get("public_identifier_value"));
        assertEquals(1, vault.count(OnboardingFixtures.IDENTIFIER_DATA_TYPE));
        // 退回后改资料使原声明失效（声明绑定资料版本），重提前须逐项重新确认。
        OnboardingFixtures.INDIVIDUAL_DECLARATIONS.forEach(type -> assertEquals(200, redeclare(app, type).getStatus().value()));

        submitAndWaitUnderReview(app, applications.nextId());

        var keys = mapper.identityKeys(app.partyId());
        assertEquals(2, keys.size());
        var newKey = keys.stream().filter(key -> OnboardingFixtures.passportToken(normalized).equals(key.get("identifier_key_value")))
            .findFirst().orElseThrow();
        assertEquals(newKey.get("identity_key_id"), mapper.identifier(app.profileId()).get("identity_key_id"));
        assertNotEquals(oldKeyId, newKey.get("identity_key_id"));
        assertTrue(keys.stream().anyMatch(key -> oldKeyId.equals(key.get("identity_key_id"))));
    }

    /** 提交后等待审核登记完成，返回可用于发布审核结果的任务。 */
    ReviewFixture submitAndWaitUnderReview(ApplicationFixture app, long caseId) {
        owners.mockReviewRegistered(caseId);
        var result = http.post().uri("/client-onboarding/v1/applications/{id}/submissions", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-resubmit-test")
            .exchange().expectBody(byte[].class).returnResult();
        assertEquals(202, result.getStatus().value(), new String(result.getResponseBody()));
        String submissionId = body(result).path("submissionId").asString();
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        return new ReviewFixture(app, submissionId, caseId, null);
    }

    /** 通过真实 HTTP 调用 CM-6 重新确认一项声明。 */
    EntityExchangeResult<byte[]> redeclare(ApplicationFixture app, String type) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/declarations", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-resubmit-test")
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("declarationType", type, "declaredValue", "TRUE"))).exchange().expectBody(byte[].class)
            .returnResult();
    }

    /** 通过真实 HTTP 调用 C04，只改证件号。 */
    EntityExchangeResult<byte[]> saveIdentifierValue(ApplicationFixture app, String value) {
        var patch = Map.of("dirtyFields", List.of("identifier.identifierValue"),
            "values", Map.of("identifier", Map.of("identifierValue", value)));
        return http.patch().uri("/client-onboarding/v1/applications/{id}/draft", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-resubmit-test")
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(patch)).exchange().expectBody(byte[].class).returnResult();
    }
}
