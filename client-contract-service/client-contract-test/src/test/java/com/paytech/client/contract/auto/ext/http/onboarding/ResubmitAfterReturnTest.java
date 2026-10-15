package com.paytech.client.contract.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.ext.mq.ClientReviewResultListener;
import com.paytech.client.contract.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.contract.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.contract.test.mocker.onboarding.OnboardingApplicationMocker.ReviewFixture;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;

import tools.jackson.databind.json.JsonMapper;

/**
 * 退回后修改证件再提交：C14 提交 → Risk 退回 → C04 改证件号 → C14 重提，按新号码绑定新身份键，旧键保留原归属。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 */
class ResubmitAfterReturnTest extends AbstractClientReviewTest {
    private static final String HASH = "a".repeat(64);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    AmqpAdmin amqpAdmin;

    /** 个人身份证明为必需材料；结果队列只保留本例消息。 */
    @BeforeEach
    void setUpReturnFlow() {
        owners.mockRequirements("INDIVIDUAL_KYC", new RequirementBody("IDENTITY", "IDENTITY_DOCUMENT", true));
        amqpAdmin.purgeQueue(ClientReviewResultListener.QUEUE, false);
    }

    /** 退回后改证件号：保存成功并解绑，重提绑定新键，旧键仍归本主体。 */
    @Test
    void test_changeIdentifierAfterReturn() throws Exception {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(fileId, HASH, "AVAILABLE");
        applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", fileId, HASH);
        var review = submitAndWaitUnderReview(app, applications.nextId());
        Object oldKeyId = mapper.identifier(app.profileId()).get("identity_key_id");

        publishResult(review, review.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(app.applicationId()).get("status")), Duration.ofSeconds(10));

        var saved = saveIdentifierValue(app, "N99-" + app.partyId() % 1_000_000);

        assertEquals(200, saved.getStatus().value(), new String(saved.getResponseBody()));
        assertNull(mapper.identifier(app.profileId()).get("identity_key_id"));

        submitAndWaitUnderReview(app, applications.nextId());

        var keys = mapper.identityKeys(app.partyId());
        assertEquals(2, keys.size());
        var newKey = keys.stream().filter(key -> ("N99" + app.partyId() % 1_000_000).equals(key.get("normalized_identifier_value")))
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
