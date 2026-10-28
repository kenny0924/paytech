package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.api.enums.OnboardingRemediationCode;
import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.enums.RuleScopeType;
import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

import tools.jackson.databind.JsonNode;

/**
 * 声明绑定资料版本与条款版本：草稿、材料、关系写入使声明失效，提交被拒，重新确认后可提交；条款版本缺失或过期拒绝写入。
 *
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi#declare(String, String, String, String, String, String,
 *      com.paytech.client.master.api.onboarding.request.DeclarationRequestBody)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#read(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save(String, String, String, String,
 *      com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody)
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi#associate(String, String, String, String,
 *      com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody)
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi#remove(String, String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi#removeRelation(String, String, String, String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#formSchema(String, String, String, String, String, String,
 *      String)
 */
class DeclarationRevisionTest extends AbstractOnboardingHttpTest {
    /** 测试配置的条款当前版本。 */
    private static final String DOCUMENT_VERSION = "2026-10-draft";
    /** 夹具 v1 Schema 中必签且附条款的声明。 */
    private static final String TERMS = "PLATFORM_TERMS";

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        owners.mockStandardPrograms();
    }

    /** 存量声明没有资料版本，申请资料版本为 0 时视为当前有效：可直接提交核对通过，读回 current=true、draftRevision=0。 */
    @Test
    void test_legacyDeclarationCurrentOnRevisionZero() {
        var app = individualWithDocument();

        assertEquals(List.of(), checkErrors(app.applicationId()));
        var draft = draft(app.applicationId());
        assertEquals("0", draft.path("draftRevision").asString());
        assertEquals(OnboardingFixtures.INDIVIDUAL_DECLARATIONS.size(), draft.path("declarations").size());
        draft.path("declarations").forEach(declaration -> {
            assertTrue(declaration.path("current").asBoolean(), declaration.toString());
            assertEquals("0", declaration.path("draftRevision").asString());
        });
    }

    /** 声明后改草稿：资料版本加一，全部声明失效，提交 422 DECLARATION_STALE；逐项重新确认后提交通过。 */
    @Test
    void test_draftEditStalesDeclarationsUntilRedeclared() {
        var app = individualWithDocument();

        saved(app.applicationId(), Map.of("attributes", Map.of("OCCUPATION", "EMPLOYEE")));

        assertEquals(1L, mapper.draftRevision(app.applicationId()));
        var errors = checkErrors(app.applicationId());
        assertTrue(errors.contains("declaration." + TERMS + "=" + OnboardingRequirementCode.DECLARATION_STALE.getCode()), errors.toString());
        assertTrue(errors.stream().allMatch(item -> item.endsWith("=" + OnboardingRequirementCode.DECLARATION_STALE.getCode())),
            errors.toString());
        var rejected = submit(app.applicationId());
        assertEquals(422, rejected.getStatus().value());
        assertEquals(ClientDraftErrorCode.SUBMISSION_INCOMPLETE.getCode(), body(rejected).path("code").asString());
        assertTrue(new String(rejected.getResponseBody()).contains(OnboardingRequirementCode.DECLARATION_STALE.getCode() + "/"
            + OnboardingRemediationCode.SIGN_DECLARATION.getCode()), new String(rejected.getResponseBody()));
        assertEquals(ClientApplicationStatus.DRAFT.getCode(), mapper.application(app.applicationId()).get("status"));
        var draft = draft(app.applicationId());
        assertEquals("1", draft.path("draftRevision").asString());
        draft.path("declarations").forEach(declaration -> assertFalse(declaration.path("current").asBoolean(), declaration.toString()));

        for (String type : OnboardingFixtures.INDIVIDUAL_DECLARATIONS) {
            var declared = declare(app.applicationId(), type);
            assertEquals(200, declared.getStatus().value(), new String(declared.getResponseBody()));
            assertTrue(body(declared).path("current").asBoolean());
            assertEquals("1", body(declared).path("draftRevision").asString());
        }

        assertEquals(List.of(), checkErrors(app.applicationId()));
        assertEquals(OnboardingFixtures.INDIVIDUAL_DECLARATIONS.size() * 2, mapper.declarations(app.applicationId()).size());
        owners.mockReviewRegistered(applications.nextId());
        var submitted = submit(app.applicationId());
        assertEquals(202, submitted.getStatus().value(), new String(submitted.getResponseBody()));
        waitUntil(() -> ClientApplicationStatus.UNDER_REVIEW.getCode().equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
    }

    /** 空 dirtyFields 的保存被拒、不改资料版本；同值同版本的重复声明返回原记录，不追加、不失效。 */
    @Test
    void test_emptyPatchAndRepeatedDeclarationKeepRevision() {
        var app = individualWithDocument();

        var empty = saveDraft(app.applicationId(), Map.of());
        assertEquals(422, empty.getStatus().value(), new String(empty.getResponseBody()));
        var repeated = declare(app.applicationId(), TERMS, null);
        assertEquals(200, repeated.getStatus().value(), new String(repeated.getResponseBody()));

        assertEquals(0L, mapper.draftRevision(app.applicationId()));
        assertEquals(OnboardingFixtures.INDIVIDUAL_DECLARATIONS.size(), mapper.declarations(app.applicationId()).size());
        assertTrue(body(repeated).path("current").asBoolean());
        assertEquals(List.of(), checkErrors(app.applicationId()));
    }

    /** 材料关联与删除、关系删除各使资料版本加一；同一材料重复关联与已删除材料再删除不改版本。之后声明失效。 */
    @Test
    void test_documentAndRelationWritesStale() {
        var app = applications.organization(ACCOUNT_ID);
        attachFixture(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        var person = applications.staffedPerson(app);
        attachFixture(app, person.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        assertEquals(List.of(), checkErrors(app.applicationId()));
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");

        var associated = associate(app.applicationId(), app.partyId(), fileId);
        assertEquals(201, associated.getStatus().value(), new String(associated.getResponseBody()));
        assertEquals(1L, mapper.draftRevision(app.applicationId()));
        assertTrue(associate(app.applicationId(), app.partyId(), fileId).getStatus().is2xxSuccessful());
        assertEquals(1L, mapper.draftRevision(app.applicationId()), "同一材料重复关联不是新的改动");
        String documentId = body(associated).path("documentId").asString();
        assertEquals(204, removeDocument(app.applicationId(), documentId).getStatus().value());
        assertEquals(2L, mapper.draftRevision(app.applicationId()));
        assertEquals(204, removeDocument(app.applicationId(), documentId).getStatus().value());
        assertEquals(2L, mapper.draftRevision(app.applicationId()), "已删除材料再删除不改动申请");
        var removed = http.delete().uri("/client-onboarding/v1/applications/{id}/relations/{relationId}", app.applicationId(),
            person.relationId()).headers(this::client).exchange().expectBody(byte[].class).returnResult();
        assertTrue(removed.getStatus().is2xxSuccessful(), removed.getStatus().toString());

        assertEquals(3L, mapper.draftRevision(app.applicationId()));
        var errors = checkErrors(app.applicationId());
        assertTrue(errors.contains("declaration." + TERMS + "=" + OnboardingRequirementCode.DECLARATION_STALE.getCode()), errors.toString());
    }

    /** 声明配置了文档版本：条款版本缺失或不符返回 422 DECLARATION_DOCUMENT_OUTDATED 且不追加；存量无版本声明随之失效；当前版本可写入。 */
    @Test
    void test_documentVersionEnforced() {
        var app = individualWithDocument();
        packs.rule(RuleScopeType.DECLARATION.getCode(), TERMS, RuleCode.DOCUMENT_VERSION.getCode(), DOCUMENT_VERSION);

        var errors = checkErrors(app.applicationId());
        assertEquals(List.of("declaration." + TERMS + "=" + OnboardingRequirementCode.DECLARATION_STALE.getCode()), errors);
        var missing = declare(app.applicationId(), TERMS, null);
        var outdated = declare(app.applicationId(), TERMS, "2026-09-draft");

        assertDocumentOutdated(missing);
        assertDocumentOutdated(outdated);
        assertEquals(OnboardingFixtures.INDIVIDUAL_DECLARATIONS.size(), mapper.declarations(app.applicationId()).size());
        var accepted = declare(app.applicationId(), TERMS, DOCUMENT_VERSION);
        assertEquals(200, accepted.getStatus().value(), new String(accepted.getResponseBody()));
        assertEquals(DOCUMENT_VERSION, body(accepted).path("termsVersion").asString());
        assertTrue(body(accepted).path("current").asBoolean());
        var latest = mapper.declarations(app.applicationId()).getLast();
        assertEquals(DOCUMENT_VERSION, latest.get("terms_version"));
        assertEquals(0L, ((Number)latest.get("draft_revision")).longValue());
        assertEquals(List.of(), checkErrors(app.applicationId()));
    }

    /** 表单规格为配置了文档版本的声明下发 documentVersion。 */
    @Test
    void test_formSchemaExposesDocumentVersion() {
        var before = body(formSchema("jurisdiction", OnboardingFixtures.JURISDICTION, "partyType", "INDIVIDUAL"));
        String type = before.path("declarations").get(0).path("declarationType").asString();
        packs.rule(RuleScopeType.DECLARATION.getCode(), type, RuleCode.DOCUMENT_VERSION.getCode(), "test-document-v9");

        var schema = body(formSchema("jurisdiction", OnboardingFixtures.JURISDICTION, "partyType", "INDIVIDUAL"));

        var declaration = schema.path("declarations").valueStream()
            .filter(node -> type.equals(node.path("declarationType").asString())).findFirst().orElseThrow();
        assertEquals("test-document-v9", declaration.path("documentVersion").asString());
    }

    /** 完整个人申请，附可用身份证明材料；读回资料时 Vault 可揭示其联系方式。 */
    ApplicationFixture individualWithDocument() {
        var app = applications.individual(ACCOUNT_ID);
        attachFixture(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.contactTokens(app.profileId()).forEach((type, token) -> vault.mockReveal(token, "EMAIL".equals(type)
            ? "declarant@example.test" : "+971500000001"));
        return app;
    }

    /** 以夹具方式插入一份可用材料，不经过写入口，不改资料版本。 */
    void attachFixture(ApplicationFixture app, long subjectPartyId, String requirementCode, String documentType) {
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");
        applications.document(app, subjectPartyId, requirementCode, documentType, fileId, HASH);
    }

    /** 经真实入口关联一份组织注册证明。 */
    EntityExchangeResult<byte[]> associate(long applicationId, long subjectPartyId, long fileId) {
        var document = Map.of("subjectPartyId", Long.toString(subjectPartyId), "requirementCode", "REGISTRATION", "documentType",
            "REGISTRATION_CERTIFICATE", "fileAddress", "/files/v1/" + fileId);
        return http.post().uri("/client-onboarding/v1/applications/{id}/documents", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(document)).exchange().expectBody(byte[].class)
            .returnResult();
    }

    /** 经真实入口删除材料引用。 */
    EntityExchangeResult<byte[]> removeDocument(long applicationId, String documentId) {
        return http.delete().uri("/client-onboarding/v1/applications/{id}/documents/{documentId}", applicationId, documentId)
            .headers(this::client).exchange().expectBody(byte[].class).returnResult();
    }

    /** 422 DECLARATION_DOCUMENT_OUTDATED，字段错误落在 termsVersion。 */
    static void assertDocumentOutdated(EntityExchangeResult<byte[]> result) {
        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        JsonNode problem = body(result);
        assertEquals(ClientDraftErrorCode.DECLARATION_DOCUMENT_OUTDATED.getCode(), problem.path("code").asString());
        assertEquals(1, problem.path("errors").size());
        assertEquals("termsVersion", problem.path("errors").get(0).path("field").asString());
    }
}
