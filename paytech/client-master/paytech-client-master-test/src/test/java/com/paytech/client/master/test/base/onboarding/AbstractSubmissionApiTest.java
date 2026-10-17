package com.paytech.client.master.test.base.onboarding;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.risk.api.compliance.MaterialRequirementsModels.RequirementBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** C14 与提交核对的入口调用及公共材料配置；不含 @Test。 */
public abstract class AbstractSubmissionApiTest extends ClientMasterMockTestBase {
    /** 文件内容摘要。 */
    protected static final String HASH = "a".repeat(64);

    private final ObjectMapper json = new ObjectMapper();

    /** 通过真实 HTTP 调用 C14。 */
    protected EntityExchangeResult<byte[]> submit(ApplicationFixture app, long accountId) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/submissions", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(accountId))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-submission-test")
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 以默认账号调用 C14。 */
    protected EntityExchangeResult<byte[]> submit(ApplicationFixture app) {
        return submit(app, ACCOUNT_ID);
    }

    /** 通过真实 HTTP 调用提交核对。 */
    protected EntityExchangeResult<byte[]> check(ApplicationFixture app) {
        return http.get().uri("/client-onboarding/v1/applications/{id}/submission-check", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-submission-test")
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 响应 JSON。 */
    protected JsonNode body(EntityExchangeResult<byte[]> result) {
        return json.readTree(result.getResponseBody());
    }

    /** 三类资格的通用材料要求：个人/关联人员身份证明必需，组织注册证明必需。 */
    protected void mockGenericRequirements() {
        owners.mockRequirements("INDIVIDUAL_KYC", new RequirementBody("IDENTITY", "IDENTITY_DOCUMENT", true),
            new RequirementBody("ADDRESS", "PROOF_OF_ADDRESS", false));
        owners.mockRequirements("ORGANIZATION_KYB", new RequirementBody("REGISTRATION", "REGISTRATION_CERTIFICATE", true));
        owners.mockRequirements("RELATED_PERSON_KYC", new RequirementBody("IDENTITY", "IDENTITY_DOCUMENT", true));
    }

    /** 关联一份可用材料。 */
    protected long availableDocument(ApplicationFixture app, long subjectPartyId, String requirementCode, String documentType) {
        long fileId = applications.nextId();
        owners.mockFile(fileId, HASH, "AVAILABLE");
        return applications.document(app, subjectPartyId, requirementCode, documentType, fileId, HASH);
    }
}
