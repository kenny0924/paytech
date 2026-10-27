package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;
import com.paytech.platform.support.api.files.FileAddress;

/**
 * C14 正式提交与提交核对：检查、状态防重、身份绑定、审核登记及依赖失败。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 */
class SubmitClientApplicationTest extends AbstractSubmissionApiTest {

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        mockGenericRequirements();
    }

    /** 个人完整申请：202、以 Vault Token 绑定身份、I01 v2 登记（只带 Token 与脱敏值）后进入审核中；重复提交返回原提交且不再检查。 */
    @Test
    void test_individualSubmittedAndRegistered() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");
        long documentId = applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", fileId, HASH);
        owners.mockReviewRegistered(880001L);

        var result = submit(app);

        assertEquals(202, result.getStatus().value(), new String(result.getResponseBody()));
        var body = body(result);
        String submissionId = body.path("submissionId").asString();
        assertTrue(submissionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertEquals("SUBMITTED", body.path("status").asString());
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        var row = mapper.application(app.applicationId());
        assertEquals(submissionId, row.get("current_submission_id"));
        assertEquals(880001L, ((Number)row.get("due_diligence_case_id")).longValue());
        String passport = OnboardingFixtures.passport(app.partyId());
        String token = OnboardingFixtures.passportToken(passport);
        var keys = mapper.identityKeys(app.partyId());
        assertEquals(1, keys.size());
        assertEquals(OnboardingFixtures.PASSPORT_COUNTRY, keys.getFirst().get("issuer_scope_key"));
        assertEquals(token, keys.getFirst().get("identifier_key_value"));
        assertEquals(keys.getFirst().get("identity_key_id"), mapper.identifier(app.profileId()).get("identity_key_id"));
        var request = owners.reviewRequests().getFirst();
        assertEquals(submissionId, request.getSourceSubmissionId());
        assertEquals("INDIVIDUAL", request.getClientType());
        assertEquals(OnboardingFixtures.JURISDICTION, request.getJurisdictionCode());
        assertEquals(1, request.getSubjects().size());
        var subject = request.getSubjects().getFirst();
        assertEquals("PRIMARY", subject.getSubjectRole());
        assertEquals(token, subject.getIdentifierVaultToken());
        assertEquals(OnboardingFixtures.masked(passport), subject.getIdentifierMasked());
        assertEquals("2031-01-02", subject.getValidTo());
        assertEquals(Long.toString(documentId), request.getDocuments().getFirst().getDocumentId());
        assertEquals(FileAddress.of(fileId).value(), request.getDocuments().getFirst().getFileAddress());
        assertEquals(HASH, request.getDocuments().getFirst().getContentHash());
        assertEquals("50000", request.getBusiness().getExpectedVolume());
        assertTrue(request.getAttributes().stream().anyMatch(item -> "OCCUPATION".equals(item.getAttributeCode())
            && "EMPLOYEE".equals(item.getAttributeValue())));
        assertEquals(OnboardingFixtures.INDIVIDUAL_DECLARATIONS.size(), request.getDeclarations().size());
        assertFalse(request.toString().contains(passport));

        var again = submit(app);

        assertEquals(200, again.getStatus().value());
        assertEquals(submissionId, body(again).path("submissionId").asString());
        assertEquals("UNDER_REVIEW", body(again).path("status").asString());
        assertEquals(1, owners.count("review:"));
        assertEquals(1, owners.count("program:"));
    }

    /** 提交前核对：完整申请可以提交，且核对不改变状态。 */
    @Test
    void test_checkReadyWithoutStateChange() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");

        var result = check(app);

        assertEquals(200, result.getStatus().value());
        assertTrue(body(result).path("ready").asBoolean(), body(result).toString());
        assertEquals(0, body(result).path("errors").size());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
    }

    /** 缺必填与必需材料：C14 返回 422 且 Problem errors 逐项给出 需求键 与 错误码/补救动作，不改变任何事实。 */
    @Test
    void test_incompleteRejectedAndListed() {
        var app = applications.individual(ACCOUNT_ID);
        mapper.clear("t_individual_profile", "given_name", app.profileId());

        var result = submit(app);

        assertEquals(422, result.getStatus().value());
        assertEquals("SUBMISSION_INCOMPLETE", body(result).path("code").asString());
        var problem = body(result).path("errors").toString();
        assertTrue(problem.contains("\"field\":\"individual.givenName\""), problem);
        assertTrue(problem.contains("FIELD_MISSING/PROVIDE_FIELD"), problem);
        assertTrue(problem.contains("DOCUMENT_MISSING/UPLOAD_DOCUMENT"), problem);
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertNull(mapper.application(app.applicationId()).get("current_submission_id"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
        assertEquals(0, mapper.reviewEventCount());
        var errors = checkErrors(app);
        assertTrue(errors.contains("individual.givenName=FIELD_MISSING"), errors.toString());
        assertTrue(errors.contains("document.IDENTITY=DOCUMENT_MISSING"), errors.toString());
    }

    /** 证件 Token 已属于其他主体：409 且不透露归属，核对在证件组上给出冲突。 */
    @Test
    void test_identityConflict() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        long owner = applications.foreignIdentity(OnboardingFixtures.PASSPORT, OnboardingFixtures.PASSPORT_COUNTRY,
            OnboardingFixtures.passportToken(OnboardingFixtures.passport(app.partyId())));

        var result = submit(app);

        assertEquals(409, result.getStatus().value());
        assertEquals("IDENTITY_CONFLICT", body(result).path("code").asString());
        assertFalse(new String(result.getResponseBody()).contains(Long.toString(owner)), "不得透露占用者");
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertTrue(mapper.identityKeys(app.partyId()).isEmpty());
        assertTrue(checkErrors(app).contains("group.PERSON_ID=IDENTIFIER_CONFLICT"));
    }

    /** 组织须有关联人员并满足 LLC 门槛（董事、签字人）；补齐后提交，主体与人员都绑定身份并一起登记。 */
    @Test
    void test_organizationRequiresPeople() {
        var app = applications.organization(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        var empty = checkErrors(app);
        assertTrue(empty.contains("people=RELATED_PERSON_INCOMPLETE"), empty.toString());

        var ubo = applications.person(app, "UBO");
        availableDocument(app, ubo.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        var partial = checkErrors(app);
        assertTrue(partial.contains("relation.DIRECTOR=DIRECTOR_MISSING"), partial.toString());
        assertTrue(partial.contains("relation.SIGNATORY=SIGNATORY_MISSING"), partial.toString());
        assertFalse(partial.stream().anyMatch(item -> item.startsWith("relation.UBO")), partial.toString());

        var director = applications.person(app, "DIRECTOR", "SIGNATORY");
        availableDocument(app, director.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockReviewRegistered(880002L);
        var result = submit(app);

        assertEquals(202, result.getStatus().value(), new String(result.getResponseBody()));
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(app.applicationId()).get("status")), Duration.ofSeconds(10));
        var orgKeys = mapper.identityKeys(app.partyId());
        assertEquals(1, orgKeys.size());
        assertEquals(OnboardingFixtures.REGISTRATION_AUTHORITY, orgKeys.getFirst().get("issuer_scope_key"));
        assertEquals(OnboardingFixtures.tradeLicense(app.partyId()), orgKeys.getFirst().get("identifier_key_value"));
        assertEquals(1, mapper.identityKeys(ubo.partyId()).size());
        var request = owners.reviewRequests().getFirst();
        assertEquals(3, request.getSubjects().size());
        assertEquals("RELATED_PERSON_KYC", request.getSubjects().get(1).getQualificationType());
        assertEquals(3, request.getRelations().size());
        assertEquals("40", request.getRelations().getFirst().getOwnershipPercentage());
        assertEquals(OnboardingFixtures.ORGANIZATION_TYPE, request.getOrganizationTypeCode());
        assertEquals(OnboardingFixtures.ORGANIZATION_DECLARATIONS.size(), request.getDeclarations().size());
    }

    /** 文件扫描未通过：必交材料不可读，C14 拒绝；可选材料字节变化不阻止。 */
    @Test
    void test_unusableFiles() {
        var app = applications.individual(ACCOUNT_ID);
        long pendingFile = applications.nextId(), changedFile = applications.nextId();
        owners.mockFile(ACCOUNT_ID, pendingFile, HASH, "SCANNING");
        owners.mockFile(ACCOUNT_ID, changedFile, "b".repeat(64), "AVAILABLE");
        applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", pendingFile, HASH);
        applications.document(app, app.partyId(), "ADDRESS", "PROOF_OF_ADDRESS", changedFile, HASH);

        var errors = checkErrors(app);

        assertEquals(java.util.List.of("document.IDENTITY=DOCUMENT_UNREADABLE"), errors);
        assertEquals(422, submit(app).getStatus().value());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
    }

    /** 材料文件不是当前账号上传：该材料不可用，C14 拒绝且不登记审核。 */
    @Test
    void test_uploaderNotCurrentAccount() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID + 1, fileId, HASH, "AVAILABLE");
        applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", fileId, HASH);

        assertTrue(checkErrors(app).contains("document.IDENTITY=DOCUMENT_UNREADABLE"));
        var result = submit(app);
        assertEquals(422, result.getStatus().value());
        assertEquals("SUBMISSION_INCOMPLETE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertEquals(0, mapper.reviewEventCount());
    }

    /** Platform 文件查询 503：C14 整体 503，申请不变。 */
    @Test
    void test_platformUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFileUnavailable(fileId);
        applications.document(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT", fileId, HASH);

        var result = submit(app);

        assertEquals(503, result.getStatus().value());
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertEquals(0, mapper.reviewEventCount());
    }

    /** compliance 程序不可用或无可用程序：503，申请不变，不视为无需材料。 */
    @Test
    void test_requirementsUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockProgramUnavailable("INDIVIDUAL", "CLIENT");

        var result = submit(app);

        assertEquals(503, result.getStatus().value());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertEquals(0, mapper.reviewEventCount());

        owners.mockProgramMissing("INDIVIDUAL", "CLIENT");
        assertEquals(503, submit(app).getStatus().value());
        assertEquals(0, mapper.reviewEventCount());
    }

    /** 个人规则矩阵：未满最低年龄、地址组件不在目录、证件已过期、缺声明，逐项给出错误码，C14 拒绝。 */
    @Test
    void test_individualRuleMatrix() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.set("t_individual_profile", "birth_date", java.sql.Date.valueOf(java.time.LocalDate.now().minusYears(17)), app.profileId());
        mapper.set("t_party_address", "state_or_province", "SA_RIYADH", app.profileId());
        mapper.set("t_party_profile_identifier", "expiry_date", java.sql.Date.valueOf("2020-01-01"), app.profileId());
        mapper.deleteDeclaration(app.applicationId(), "PLATFORM_TERMS");

        var errors = checkErrors(app);

        assertTrue(errors.contains("individual.birthDate=DOB_AGE_UNDER_MINIMUM"), errors.toString());
        assertTrue(errors.contains("address.stateOrProvince=FIELD_VALUE_NOT_IN_CATALOG"), errors.toString());
        assertTrue(errors.contains("group.PERSON_ID=IDENTIFIER_EXPIRED"), errors.toString());
        assertTrue(errors.contains("declaration.PLATFORM_TERMS=DECLARATION_MISSING"), errors.toString());
        assertEquals(422, submit(app).getStatus().value());
        assertEquals(0, mapper.reviewEventCount());
    }

    /** 组织规则矩阵：受益人持股合计超过 100%，执照已过期，关联人员缺字段时以 people[人员] 前缀报告。 */
    @Test
    void test_organizationRuleMatrix() {
        var app = applications.organization(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        var first = applications.staffedPerson(app);
        var second = applications.person(app, "UBO");
        availableDocument(app, first.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        availableDocument(app, second.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.ownership(first.relationId(), new java.math.BigDecimal("60"));
        mapper.ownership(second.relationId(), new java.math.BigDecimal("50"));
        mapper.set("t_organization_profile", "license_expiry_date", java.sql.Date.valueOf("2020-01-01"), app.profileId());
        mapper.clear("t_individual_profile", "birth_place", second.profileId());

        var errors = checkErrors(app);

        assertTrue(errors.contains("relation.UBO_TOTAL=UBO_OWNERSHIP_EXCEEDS_100"), errors.toString());
        assertTrue(errors.contains("organization.licenseExpiryDate=IDENTIFIER_EXPIRED"), errors.toString());
        assertTrue(errors.contains("individual.birthPlace=FIELD_MISSING"), errors.toString());
        var problem = new String(submit(app).getResponseBody());
        assertTrue(problem.contains("\"field\":\"people[" + second.partyId() + "].individual.birthPlace\""), problem);
        assertTrue(problem.contains("\"field\":\"relation.UBO_TOTAL\""), problem);
    }

    /** 关联人员程序的身份证明（form-schema 以 ANY_RELATED 下发）同样约束提交：人员缺材料时核对与 422 都指向该人员。 */
    @Test
    void test_relatedPersonMaterialRequired() {
        var app = applications.organization(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        var person = applications.staffedPerson(app);

        var result = check(app);

        var missing = body(result).path("errors").valueStream().filter(item -> "document.IDENTITY".equals(item.path("requirementKey")
            .asString())).toList();
        assertEquals(1, missing.size(), body(result).toString());
        assertEquals(Long.toString(person.partyId()), missing.getFirst().path("subjectPartyId").asString());
        assertEquals("DOCUMENT_MISSING", missing.getFirst().path("code").asString());
        var problem = new String(submit(app).getResponseBody());
        assertTrue(problem.contains("\"field\":\"people[" + person.partyId() + "].document.IDENTITY\""), problem);

        availableDocument(app, person.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");

        assertTrue(body(check(app)).path("ready").asBoolean(), body(check(app)).toString());
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
