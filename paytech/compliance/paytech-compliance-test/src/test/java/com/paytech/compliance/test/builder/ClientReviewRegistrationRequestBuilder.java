package com.paytech.risk.test.builder.compliance;

import static com.paytech.risk.api.enums.MaterialQualificationType.INDIVIDUAL_KYC;
import static com.paytech.risk.api.enums.MaterialQualificationType.ORGANIZATION_KYB;
import static com.paytech.risk.api.enums.MaterialQualificationType.RELATED_PERSON_KYC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import com.paytech.risk.api.compliance.request.ClientReviewBusinessBody;
import com.paytech.risk.api.compliance.request.ClientReviewDocumentBody;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewRelationBody;
import com.paytech.risk.api.compliance.request.ClientReviewSubjectBody;

/**
 * I01 审核登记请求 Builder
 * <li>organization() 为组织客户 + 两名关联人员（甲 UBO 40% 兼 DIRECTOR，乙 SIGNATORY），每个主体一份基础必需材料。</li>
 * <li>individual() 为个人客户，只有主主体及一份身份材料。</li>
 * <li>申请、客户、主体、资料、关系、材料与文件 ID 均为随机 15 位正整数，提交编号为随机 UUID；资料均为合成值，不含真实个人信息。</li>
 * <li>build() 原样返回请求，不修复用例刻意设置的非法值。</li>
 *
 * @author Kenny
 */
public final class ClientReviewRegistrationRequestBuilder {
    /** 组织主体的基础必需材料要求代码与类型。 */
    public static final String REGISTRATION_CERTIFICATE = "REGISTRATION_CERTIFICATE";

    public static final String COMPANY_REGISTRY_EXTRACT = "COMPANY_REGISTRY_EXTRACT";

    /** 个人与关联人员的基础必需材料要求代码与类型。 */
    public static final String IDENTITY_DOCUMENT = "IDENTITY_DOCUMENT";

    public static final String PASSPORT = "PASSPORT";

    /** 所有资格类型共有的非必需材料要求代码与类型。 */
    public static final String PROOF_OF_ADDRESS = "PROOF_OF_ADDRESS";

    public static final String UTILITY_BILL = "UTILITY_BILL";

    /** 合成文件 SHA-256。 */
    public static final String CONTENT_HASH = "c".repeat(64);

    /** 固定的合成提交时间，带微秒便于比较 Wire 时间。 */
    public static final String SUBMITTED_AT = "2026-10-13T08:00:00.123456Z";

    private final ClientReviewRegistrationRequestBody body = new ClientReviewRegistrationRequestBody();

    private ClientReviewRegistrationRequestBuilder(String clientType) {
        long application = randomId();
        body.setSourceSubmissionId(UUID.randomUUID().toString());
        body.setApplicationId(Long.toString(application));
        body.setApplicationNo("TA" + application);
        body.setClientId(Long.toString(randomId()));
        body.setClientType(clientType);
        body.setRequestedByAccountId(Long.toString(randomId()));
        body.setSubmittedAt(SUBMITTED_AT);
        body.setBusiness(business());
        body.setSubjects(new ArrayList<>());
        body.setRelations(new ArrayList<>());
        body.setDocuments(new ArrayList<>());
    }

    /** 组织客户及两名关联人员，各有一份基础必需材料。 */
    public static ClientReviewRegistrationRequestBuilder organization() {
        ClientReviewRegistrationRequestBuilder builder = new ClientReviewRegistrationRequestBuilder("ORGANIZATION");
        String organization = builder.addSubject("PRIMARY", ORGANIZATION_KYB.getCode(), true);
        String personA = builder.addSubject("RELATED_PERSON", RELATED_PERSON_KYC.getCode(), false);
        String personB = builder.addSubject("RELATED_PERSON", RELATED_PERSON_KYC.getCode(), false);
        builder.addRelation(personA, "UBO", "40").addRelation(personA, "DIRECTOR", null)
            .addRelation(personB, "SIGNATORY", null);
        builder.addDocument(organization, REGISTRATION_CERTIFICATE, COMPANY_REGISTRY_EXTRACT)
            .addDocument(personA, IDENTITY_DOCUMENT, PASSPORT).addDocument(personB, IDENTITY_DOCUMENT, PASSPORT);
        return builder;
    }

    /** 个人客户，只有主主体及一份身份材料。 */
    public static ClientReviewRegistrationRequestBuilder individual() {
        ClientReviewRegistrationRequestBuilder builder = new ClientReviewRegistrationRequestBuilder("INDIVIDUAL");
        String person = builder.addSubject("PRIMARY", INDIVIDUAL_KYC.getCode(), false);
        return builder.addDocument(person, IDENTITY_DOCUMENT, PASSPORT);
    }

    /**
     * 追加一个受审主体
     *
     * @param role PRIMARY 或 RELATED_PERSON
     * @param qualificationType 资格类型代码
     * @param organization 是否填写组织资料，否则填写个人资料
     * @return 新主体的 Party ID
     */
    public String addSubject(String role, String qualificationType, boolean organization) {
        String partyId = Long.toString(randomId());
        var subject = new ClientReviewSubjectBody();
        subject.setPartyId(partyId);
        subject.setProfileId(Long.toString(randomId()));
        subject.setSubjectRole(role);
        subject.setQualificationType(qualificationType);
        if (organization) {
            subject.setLegalName("Synthetic LLC");
            subject.setIncorporationCountry("AE");
            subject.setOrganizationType("LIMITED_COMPANY");
            subject.setIncorporationDate("2020-01-15");
        } else {
            subject.setGivenName("Synthetic");
            subject.setFamilyName("Person");
            subject.setBirthDate("1990-05-01");
            subject.setNationalityCountry("GB");
            subject.setResidenceCountry("AE");
        }
        subject.setAddressLine1("1 Synthetic Road");
        subject.setCity("Dubai");
        subject.setAddressCountry("AE");
        subject.setIdentifierType(organization ? "COMPANY_REGISTRATION_NUMBER" : "PASSPORT");
        subject.setIssuingCountry("GB");
        subject.setIdentifierValue("SYN" + partyId);
        subject.setIdentityKeyId(Long.toString(randomId()));
        body.getSubjects().add(subject);
        return partyId;
    }

    /** 追加一条关系；持股比例可空。 */
    public ClientReviewRegistrationRequestBuilder addRelation(String relatedPartyId, String relationType,
        String ownershipPercentage) {
        var relation = new ClientReviewRelationBody();
        relation.setRelationId(Long.toString(randomId()));
        relation.setRelatedPartyId(relatedPartyId);
        relation.setRelationType(relationType);
        relation.setOwnershipPercentage(ownershipPercentage);
        relation.setEffectiveFrom("2024-01-01");
        body.getRelations().add(relation);
        return this;
    }

    /** 追加一份材料，文件 ID 随机。 */
    public ClientReviewRegistrationRequestBuilder addDocument(String subjectPartyId, String requirementCode,
        String documentType) {
        var document = new ClientReviewDocumentBody();
        document.setDocumentId(Long.toString(randomId()));
        document.setSubjectPartyId(subjectPartyId);
        document.setRequirementCode(requirementCode);
        document.setDocumentType(documentType);
        document.setFileId(Long.toString(randomId()));
        document.setContentHash(CONTENT_HASH);
        body.getDocuments().add(document);
        return this;
    }

    /** 修改请求顶层字段。 */
    public ClientReviewRegistrationRequestBuilder with(Consumer<ClientReviewRegistrationRequestBody> change) {
        change.accept(body);
        return this;
    }

    /** 修改第 index 个主体（0 为主主体）。 */
    public ClientReviewRegistrationRequestBuilder subject(int index, Consumer<ClientReviewSubjectBody> change) {
        change.accept(body.getSubjects().get(index));
        return this;
    }

    /** 修改第 index 条关系。 */
    public ClientReviewRegistrationRequestBuilder relation(int index, Consumer<ClientReviewRelationBody> change) {
        change.accept(body.getRelations().get(index));
        return this;
    }

    /** 修改第 index 份材料。 */
    public ClientReviewRegistrationRequestBuilder document(int index, Consumer<ClientReviewDocumentBody> change) {
        change.accept(body.getDocuments().get(index));
        return this;
    }

    /** 修改经营资料。 */
    public ClientReviewRegistrationRequestBuilder business(Consumer<ClientReviewBusinessBody> change) {
        change.accept(body.getBusiness());
        return this;
    }

    /** 第 index 个主体的 Party ID。 */
    public String partyId(int index) {
        return body.getSubjects().get(index).getPartyId();
    }

    /** 当前请求；返回同一对象，便于重放同一内容。 */
    public ClientReviewRegistrationRequestBody build() {
        return body;
    }

    /** 合成经营资料。 */
    private static ClientReviewBusinessBody business() {
        var business = new ClientReviewBusinessBody();
        business.setBusinessModelCode("SERVICES");
        business.setBusinessDescription("Synthetic services");
        business.setExpectedVolume("50000");
        business.setExpectedTicket("25.5");
        business.setCurrency("USD");
        return business;
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    public static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 1_000_000_000_000_000L);
    }

    /** 当前请求中某主体的全部材料要求代码。 */
    public List<String> documentCodes(String partyId) {
        return body.getDocuments().stream().filter(document -> partyId.equals(document.getSubjectPartyId()))
            .map(ClientReviewDocumentBody::getRequirementCode).toList();
    }
}
