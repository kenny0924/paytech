package com.paytech.compliance.test.builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.paytech.compliance.api.request.ClientReviewBusinessBody;
import com.paytech.compliance.api.request.ClientReviewDeclarationBody;
import com.paytech.compliance.api.request.ClientReviewDocumentBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.request.ClientReviewRelationBody;
import com.paytech.compliance.api.request.ClientReviewSubjectBody;

/**
 * I01 v2 请求 Builder：合法默认值（随机申请、Client、Party 与资料 ID），按辖区与主体类型组装
 * <li>资料 ID 与 Party ID 相差 1（与 PartyProfileMocker 一致），便于为每个主体配置筛查名称。</li>
 * <li>材料由用例按程序材料槽追加；默认不带材料。</li>
 */
public class ClientReviewRegistrationRequestBuilder {
    private final ClientReviewRegistrationRequestBody body = new ClientReviewRegistrationRequestBody();

    /** 机构 Client 模板。 */
    public static ClientReviewRegistrationRequestBuilder organization(String jurisdiction, String organizationType) {
        ClientReviewRegistrationRequestBuilder builder = base(jurisdiction, "ORGANIZATION");
        builder.body.setOrganizationTypeCode(organizationType);
        builder.body.getSubjects().add(subject(id(), "PRIMARY", "ORGANIZATION_KYB"));
        return builder;
    }

    /** 个人 Client 模板。 */
    public static ClientReviewRegistrationRequestBuilder individual(String jurisdiction) {
        ClientReviewRegistrationRequestBuilder builder = base(jurisdiction, "INDIVIDUAL");
        builder.body.getSubjects().add(subject(id(), "PRIMARY", "INDIVIDUAL_KYC"));
        return builder;
    }

    /** 公共字段。 */
    static ClientReviewRegistrationRequestBuilder base(String jurisdiction, String clientType) {
        ClientReviewRegistrationRequestBuilder builder = new ClientReviewRegistrationRequestBuilder();
        ClientReviewRegistrationRequestBody body = builder.body;
        body.setSourceSubmissionId(UUID.randomUUID().toString());
        body.setApplicationId(Long.toString(id()));
        body.setApplicationNo("APP" + id());
        body.setClientId(Long.toString(id()));
        body.setClientType(clientType);
        body.setJurisdictionCode(jurisdiction);
        body.setRequestedByAccountId(Long.toString(id()));
        body.setSubmittedAt(Instant.now().toString());
        ClientReviewBusinessBody business = new ClientReviewBusinessBody();
        business.setBusinessModelCode("ONLINE_RETAIL");
        business.setWebsiteUrl("https://merchant.example");
        business.setBusinessDescription("Synthetic test merchant");
        business.setExpectedVolume("100000.00");
        business.setExpectedTicket("50.00");
        business.setCurrency("USD");
        body.setBusiness(business);
        body.setSubjects(new ArrayList<>());
        body.setRelations(new ArrayList<>());
        body.setDocuments(new ArrayList<>());
        ClientReviewDeclarationBody declaration = new ClientReviewDeclarationBody();
        declaration.setDeclarationType("INFORMATION_TRUE");
        declaration.setDeclaredValue("TRUE");
        declaration.setDeclaredAt(Instant.now().toString());
        body.setDeclarations(new ArrayList<>(List.of(declaration)));
        return builder;
    }

    /** 追加一名关联人员及其关系，返回其 Party ID。 */
    public long addRelatedPerson(String relationType) {
        long partyId = id();
        body.getSubjects().add(subject(partyId, "RELATED_PERSON", "RELATED_PERSON_KYC"));
        ClientReviewRelationBody relation = new ClientReviewRelationBody();
        relation.setRelationId(Long.toString(id()));
        relation.setRelatedPartyId(Long.toString(partyId));
        relation.setRelationType(relationType);
        relation.setOwnershipPercentage("UBO".equals(relationType) ? "60" : null);
        relation.setUboBasis("UBO".equals(relationType) ? "OWNERSHIP" : null);
        relation.setJobTitle("Director");
        relation.setEffectiveFrom("2024-01-01");
        body.getRelations().add(relation);
        return partyId;
    }

    /** 为主体追加一份材料，返回文件地址。 */
    public String addDocument(long partyId, String requirementCode, String documentType) {
        ClientReviewDocumentBody document = new ClientReviewDocumentBody();
        long fileId = id();
        document.setDocumentId(Long.toString(id()));
        document.setSubjectPartyId(Long.toString(partyId));
        document.setRequirementCode(requirementCode);
        document.setDocumentType(documentType);
        document.setFileAddress("/files/v1/" + fileId);
        document.setContentHash(String.format("%064x", fileId));
        document.setIssueDate("2026-09-01");
        body.getDocuments().add(document);
        return document.getFileAddress();
    }

    /** 主主体 Party ID。 */
    public long primaryPartyId() {
        return Long.parseLong(body.getSubjects().getFirst().getPartyId());
    }

    /** 全部主体 Party ID（主主体在前）。 */
    public List<Long> partyIds() {
        return body.getSubjects().stream().map(subject -> Long.parseLong(subject.getPartyId())).toList();
    }

    /** 主体的资料 ID。 */
    public static long profileIdOf(long partyId) {
        return partyId - 1;
    }

    /** 责任 PayFac。 */
    public ClientReviewRegistrationRequestBuilder responsiblePartner(Long partnerId) {
        body.setResponsiblePartnerId(partnerId == null ? null : Long.toString(partnerId));
        return this;
    }

    /** 重提：新提交编号并指向上一轮主案件。 */
    public ClientReviewRegistrationRequestBuilder resubmit(long previousCaseId) {
        body.setSourceSubmissionId(UUID.randomUUID().toString());
        body.setPreviousCaseId(Long.toString(previousCaseId));
        body.setSubmittedAt(Instant.now().toString());
        return this;
    }

    /** 直接改写请求体（负例）。 */
    public ClientReviewRegistrationRequestBody body() {
        return body;
    }

    /** 构建请求。 */
    public ClientReviewRegistrationRequestBody build() {
        return body;
    }

    /** 主体模板：Token 化证件，不含原值。 */
    static ClientReviewSubjectBody subject(long partyId, String role, String qualificationType) {
        ClientReviewSubjectBody subject = new ClientReviewSubjectBody();
        subject.setPartyId(Long.toString(partyId));
        subject.setProfileId(Long.toString(profileIdOf(partyId)));
        subject.setSubjectRole(role);
        subject.setQualificationType(qualificationType);
        subject.setIdentifierType("ORGANIZATION_KYB".equals(qualificationType) ? "SA_CR_NUMBER" : "PASSPORT");
        subject.setIssuingCountry("SA");
        subject.setIdentifierVaultToken("ORGANIZATION_KYB".equals(qualificationType) ? null : "vt_stable_" + partyId);
        subject.setIdentifierMasked("ORGANIZATION_KYB".equals(qualificationType) ? null : "****" + (partyId % 10000));
        subject.setIdentityKeyId(Long.toString(id()));
        return subject;
    }

    /** 随机正 ID（资格等其他夹具复用）。 */
    public static long randomId() {
        return id();
    }

    /** 随机正 ID。 */
    public static long id() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
