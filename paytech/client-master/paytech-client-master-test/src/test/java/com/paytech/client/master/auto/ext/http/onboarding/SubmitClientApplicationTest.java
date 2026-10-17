package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;

/**
 * C14 正式提交与提交核对：检查、状态防重、身份绑定、审核登记及依赖失败。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 */
class SubmitClientApplicationTest extends AbstractSubmissionApiTest {

    /** 本类公共配置：三类资格的通用材料要求。 */
    @BeforeEach
    void setUpClassMocks() {
        mockGenericRequirements();
    }

    /** 个人完整申请：202、绑定身份、登记审核后进入审核中；重复提交返回原提交且不再检查。 */
    @Test
    void test_individualSubmittedAndRegistered() {
        var app = applications.individual(ACCOUNT_ID);
        long documentId = availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockReviewRegistered(880001L);

        var result = submit(app);

        assertEquals(202, result.getStatus().value());
        var body = body(result);
        String submissionId = body.path("submissionId").asString();
        assertTrue(submissionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertEquals("SUBMITTED", body.path("status").asString());
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        var row = mapper.application(app.applicationId());
        assertEquals(submissionId, row.get("current_submission_id"));
        assertEquals(880001L, ((Number)row.get("due_diligence_case_id")).longValue());
        var keys = mapper.identityKeys(app.partyId());
        assertEquals(1, keys.size());
        assertEquals("GB", keys.getFirst().get("issuer_scope_key"));
        assertEquals("P12" + app.partyId() % 1_000_000, keys.getFirst().get("normalized_identifier_value"));
        assertEquals(keys.getFirst().get("identity_key_id"), mapper.identifier(app.profileId()).get("identity_key_id"));
        var request = owners.reviewRequests().getFirst();
        assertEquals(submissionId, request.getSourceSubmissionId());
        assertEquals("INDIVIDUAL", request.getClientType());
        assertEquals(1, request.getSubjects().size());
        assertEquals("PRIMARY", request.getSubjects().getFirst().getSubjectRole());
        assertEquals("p 12-" + app.partyId() % 1_000_000, request.getSubjects().getFirst().getIdentifierValue());
        assertEquals(Long.toString(documentId), request.getDocuments().getFirst().getDocumentId());
        assertEquals(HASH, request.getDocuments().getFirst().getContentHash());
        assertEquals("50000", request.getBusiness().getExpectedVolume());

        var again = submit(app);

        assertEquals(200, again.getStatus().value());
        assertEquals(submissionId, body(again).path("submissionId").asString());
        assertEquals("UNDER_REVIEW", body(again).path("status").asString());
        assertEquals(1, owners.count("review:"));
        assertEquals(1, owners.count("requirements:"));
    }

    /** 提交前核对：完整申请可以提交，且核对不改变状态。 */
    @Test
    void test_checkReadyWithoutStateChange() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");

        var result = check(app);

        assertEquals(200, result.getStatus().value());
        assertTrue(body(result).path("ready").asBoolean());
        assertEquals(0, body(result).path("issues").size());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
    }

    /** 缺必填与基础必需材料：C14 返回 422 且不改变任何事实；核对逐项列出。 */
    @Test
    void test_incompleteRejectedAndListed() {
        var app = applications.individual(ACCOUNT_ID);
        mapper.clear("t_individual_profile", "given_name", app.profileId());

        var result = submit(app);

        assertEquals(422, result.getStatus().value());
        assertEquals("SUBMISSION_INCOMPLETE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertNull(mapper.application(app.applicationId()).get("current_submission_id"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
        assertEquals(0, mapper.reviewEventCount());
        var issues = body(check(app)).path("issues").toString();
        assertTrue(issues.contains("\"individual.givenName\""), issues);
        assertTrue(issues.contains("\"materials[" + app.partyId() + "].IDENTITY\""), issues);
    }

    /** 证件号已属于其他主体：409 且不透露归属，核对给出位置。 */
    @Test
    void test_identityConflict() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        applications.foreignIdentity("PASSPORT", "GB", "P12" + app.partyId() % 1_000_000);

        var result = submit(app);

        assertEquals(409, result.getStatus().value());
        assertEquals("IDENTITY_CONFLICT", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
        assertTrue(body(check(app)).path("issues").toString().contains("\"identifier.identifierValue\""));
    }

    /** 组织至少一名关联人员；补齐人员与材料后提交，主体与人员都绑定身份并一起登记。 */
    @Test
    void test_organizationRequiresPeople() {
        var app = applications.organization(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        assertTrue(body(check(app)).path("issues").toString().contains("\"field\":\"people\""));

        var person = applications.person(app, "UBO");
        availableDocument(app, person.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockReviewRegistered(880002L);
        var result = submit(app);

        assertEquals(202, result.getStatus().value());
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        assertEquals(1, mapper.identityKeys(app.partyId()).size());
        assertEquals(1, mapper.identityKeys(person.partyId()).size());
        var request = owners.reviewRequests().getFirst();
        assertEquals(2, request.getSubjects().size());
        assertEquals("RELATED_PERSON_KYC", request.getSubjects().get(1).getQualificationType());
        assertEquals(1, request.getRelations().size());
        assertEquals("40", request.getRelations().getFirst().getOwnershipPercentage());
    }

    /** 文件扫描未通过或字节变化：材料逐项不可用，C14 拒绝。 */
    @Test
    void test_unusableFiles() {
        var app = applications.individual(ACCOUNT_ID);
        long pendingFile = applications.nextId(), changedFile = applications.nextId();
        owners.mockFile(pendingFile, HASH, "SCANNING");
        owners.mockFile(changedFile, "b".repeat(64), "AVAILABLE");
        long pending = applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", pendingFile, HASH);
        long changed = applications.document(app, app.partyId(), "ADDRESS", "PROOF_OF_ADDRESS", changedFile, HASH);

        var issues = body(check(app)).path("issues").toString();

        assertTrue(issues.contains("\"documents[" + pending + "]\""), issues);
        assertTrue(issues.contains("\"documents[" + changed + "]\""), issues);
        assertEquals(422, submit(app).getStatus().value());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
    }

    /** Risk 材料要求不可用：503，申请不变，不视为无需材料。 */
    @Test
    void test_requirementsUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockRequirementsUnavailable("INDIVIDUAL_KYC");

        var result = submit(app);

        assertEquals(503, result.getStatus().value());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertEquals(0, mapper.reviewEventCount());
    }

    /** 其他账号或非法调用方看不到申请。 */
    @Test
    void test_notVisible() {
        var app = applications.individual(ACCOUNT_ID);
        owners.mockSession(ACCOUNT_ID + 1, SESSION_ID);

        assertEquals(404, submit(app, ACCOUNT_ID + 1).getStatus().value());
        var wrongCaller = http.post().uri("/client-onboarding/v1/applications/{id}/submissions", app.applicationId())
            .header("X-Caller-Service", "unknown").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).exchange().expectBody(byte[].class).returnResult();
        assertEquals(404, wrongCaller.getStatus().value());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
    }
}
