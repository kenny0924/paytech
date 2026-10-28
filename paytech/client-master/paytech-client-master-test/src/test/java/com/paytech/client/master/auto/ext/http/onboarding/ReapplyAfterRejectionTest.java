package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
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
 * 被拒后重新申请：C14 提交 → Risk 拒绝 → C01 同类型新建申请复用原 Client 并带入资料、人员与关系 → 重传材料 → C14 同证件重提成功。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#create
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 */
class ReapplyAfterRejectionTest extends AbstractClientReviewTest {
    private static final String HASH = "a".repeat(64);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 组织与关联人员各需一份必需材料；结果分区队列由基类清空。 */
    @BeforeEach
    void setUpRejectionFlow() {
        owners.mockProgram("ORGANIZATION", "CLIENT", new Material("REGISTRATION", "REGISTRATION_CERTIFICATE", true));
        owners.mockProgram("INDIVIDUAL", "RELATED_PERSON", new Material("IDENTITY", "IDENTITY_DOCUMENT", true));
    }

    /** 同类型重新申请：原 Client 下新 DRAFT 申请，资料与人员可编辑，材料需重传；重提复用原身份键，不再开通工作区。 */
    @Test
    void test_reapplySameType() throws Exception {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.staffedPerson(app);
        uploadMaterials(app, person.partyId());
        var review = submitAndWaitUnderReview(app, applications.nextId());
        publishResult(review, review.caseId(), "REJECTED", applications.nextId(), OPERATOR_ID);
        waitUntil(() -> "REJECTED".equals(mapper.review(app.applicationId()).get("status")), Duration.ofSeconds(10));

        var started = start("ORGANIZATION");

        assertEquals(201, started.getStatus().value(), new String(started.getResponseBody()));
        long newId = Long.parseLong(body(started).path("applicationId").asString());
        applications.track(newId);
        assertNotEquals(app.applicationId(), newId);
        assertEquals(Long.toString(app.clientId()), body(started).path("clientId").asString());
        assertEquals("DRAFT", mapper.application(newId).get("status"));
        assertEquals("REJECTED", mapper.review(app.applicationId()).get("status"));
        assertEquals(newId, mapper.draftApplication(app.profileId()));
        assertEquals(newId, mapper.draftApplication(person.profileId()));
        assertEquals(newId, mapper.relationApplication(person.relationId()));
        assertEquals(0, mapper.documentCount(newId));

        var again = new ApplicationFixture(ACCOUNT_ID, newId, app.clientId(), app.partyId(), app.profileId(), true);
        uploadMaterials(again, person.partyId());
        // 声明属于申请：新申请须重新签署，未签署时提交被拒并逐项列出。
        var unsigned = http.post().uri("/client-onboarding/v1/applications/{id}/submissions", newId).headers(this::client).exchange()
            .expectBody(byte[].class).returnResult();
        assertEquals(422, unsigned.getStatus().value());
        assertTrue(new String(unsigned.getResponseBody()).contains("declaration.PLATFORM_TERMS_ACCEPTANCE"));
        // 新申请使用 v2 Schema：拆分后的声明，条款与隐私告知须带当前文档版本。
        OnboardingFixtures.ORGANIZATION_DECLARATIONS_V2.forEach(type -> declare(newId, type));
        submitAndWaitUnderReview(again, applications.nextId());

        assertEquals(1, mapper.identityKeys(app.partyId()).size());
        assertEquals(1, mapper.identityKeys(person.partyId()).size());
        assertEquals(2, owners.reviewRequests().size());
        assertTrue(owners.projectionRequests().isEmpty());
    }

    /** 主体与关联人员各关联一份可用材料。 */
    void uploadMaterials(ApplicationFixture app, long personPartyId) {
        long organizationFile = applications.nextId(), personFile = applications.nextId();
        owners.mockFile(ACCOUNT_ID, organizationFile, HASH, "AVAILABLE");
        owners.mockFile(ACCOUNT_ID, personFile, HASH, "AVAILABLE");
        applications.document(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE", organizationFile, HASH);
        applications.document(app, personPartyId, "IDENTITY", "IDENTITY_DOCUMENT", personFile, HASH);
    }

    /** 提交后等待审核登记完成，返回可用于发布审核结果的任务。 */
    ReviewFixture submitAndWaitUnderReview(ApplicationFixture app, long caseId) {
        owners.mockReviewRegistered(caseId);
        var result = http.post().uri("/client-onboarding/v1/applications/{id}/submissions", app.applicationId())
            .headers(this::client).exchange().expectBody(byte[].class).returnResult();
        assertEquals(202, result.getStatus().value(), new String(result.getResponseBody()));
        String submissionId = body(result).path("submissionId").asString();
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        return new ReviewFixture(app, submissionId, caseId, null);
    }

    /** 通过真实 HTTP 调用 C01 开始申请。 */
    EntityExchangeResult<byte[]> start(String clientType) {
        return http.post().uri("/client-onboarding/v1/applications").headers(this::client)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("clientType", clientType, "jurisdictionCode", "AE"))).exchange().expectBody(byte[].class).returnResult();
    }

    /** 通过真实 HTTP 调用 CM-6 签署一项声明；附版本化文档的声明带当前文档版本。 */
    void declare(long applicationId, String type) {
        var body = new java.util.LinkedHashMap<String, String>(Map.of("declarationType", type, "declaredValue", "TRUE"));
        String termsVersion = OnboardingFixtures.termsVersion(type);
        if (termsVersion != null) {
            body.put("termsVersion", termsVersion);
        }
        var result = http.post().uri("/client-onboarding/v1/applications/{id}/declarations", applicationId).headers(this::client)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(body))
            .exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
    }

    /** 可信 Client BFF 调用头。 */
    void client(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Caller-Service", "client-console-bff");
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
        headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
        headers.set("X-Request-Id", "cct-reapply-test");
    }
}
