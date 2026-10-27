package com.paytech.client.master.integration.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientReviewSourceView;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.compliance.api.ClientReviewRegistrationApi;
import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.domain.onboarding.ClientDraftValues;
import com.paytech.compliance.api.request.ClientReviewAttributeBody;
import com.paytech.compliance.api.request.ClientReviewBusinessBody;
import com.paytech.compliance.api.request.ClientReviewDeclarationBody;
import com.paytech.compliance.api.request.ClientReviewWithdrawalRequestBody;
import com.paytech.compliance.api.request.ClientReviewDocumentBody;
import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.api.request.ClientReviewRelationBody;
import com.paytech.compliance.api.request.ClientReviewSubjectBody;

import lombok.AllArgsConstructor;

/** 把 CCT 提交事实转换为 compliance I01 v2 请求，并承载 I02 分类交付与 I03 撤回；不在本地保存请求或响应。 */
@AllArgsConstructor
public class ClientReviewRegistrationClientImpl implements ClientReviewRegistrationClient {
    private static final String CALLER = "client-master-service";

    private final ClientReviewRegistrationApi risk;

    /** 调用 Risk 并校验返回的同一提交与任务 ID。 */
    @Override
    public long register(ClientReviewSourceView source) {
        var request = request(source);
        var response = risk.register(CALLER, request);
        if (response == null || !request.getSourceSubmissionId().equals(response.getSourceSubmissionId())
            || response.getCaseId() == null || !response.getCaseId().matches("[1-9][0-9]{0,18}")) {
            throw new ErrorException("Risk 审核登记响应不完整");
        }
        return Long.parseLong(response.getCaseId());
    }

    /**
     * 组装 I01 v2 受审事实；主主体在前
     * <li>不传证件原值与地址：个人证件只传 Vault Token 与脱敏值，机构公开登记号只传脱敏值；扩展属性与声明随附。</li>
     */
    ClientReviewRegistrationRequestBody request(ClientReviewSourceView source) {
        var application = source.getApplication();
        var identity = source.getIdentity();
        boolean organization = identity.clientType() == ClientType.ORGANIZATION;
        var body = new ClientReviewRegistrationRequestBody();
        body.setSourceSubmissionId(application.getCurrentSubmissionId());
        body.setApplicationId(Long.toString(application.getApplicationId()));
        body.setApplicationNo(application.getApplicationNo());
        body.setClientId(Long.toString(application.getClientId()));
        body.setClientType(identity.clientType().getCode());
        body.setJurisdictionCode(application.getJurisdictionCode());
        body.setOrganizationTypeCode(application.getOrganizationTypeCode());
        body.setResponsiblePartnerId(id(application.getResponsiblePartnerId()));
        body.setPreviousCaseId(id(application.getPreviousCaseId()));
        body.setRequestedByAccountId(Long.toString(application.getAccountId()));
        body.setSubmittedAt(new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(application.getSubmittedTime()));
        body.setBusiness(business(source.getPrimary()));
        var subjects = new ArrayList<ClientReviewSubjectBody>();
        subjects.add(subject(identity.partyId(), identity.profileId(), source.getPrimary(), organization, "PRIMARY",
            organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC", source));
        source.getPeople().forEach(person -> subjects.add(subject(person.identity().partyId(), person.identity().profileId(),
            person.fields(), false, "RELATED_PERSON", "RELATED_PERSON_KYC", source)));
        body.setSubjects(subjects);
        body.setRelations(source.getRelations().stream().map(this::relation).toList());
        body.setDocuments(source.getDocuments().stream().map(this::document).toList());
        body.setAttributes(attributes(source));
        body.setDeclarations(declarations(source));
        return body;
    }

    /** 经营资料。 */
    ClientReviewBusinessBody business(ClientDraftFields f) {
        var body = new ClientReviewBusinessBody();
        body.setBusinessModelCode(f.businessBusinessModelCode());
        body.setWebsiteUrl(f.businessWebsiteUrl());
        body.setBusinessDescription(f.businessBusinessDescription());
        body.setExpectedVolume(plain(f.businessExpectedVolume()));
        body.setExpectedTicket(plain(f.businessExpectedTicket()));
        body.setCurrency(f.businessCurrency());
        return body;
    }

    /** 单个主体资料及其已绑定身份键；证件只含类型、签发国、Token、脱敏值与日期。 */
    ClientReviewSubjectBody subject(long partyId, long profileId, ClientDraftFields f, boolean organization, String role,
        String qualification, ClientReviewSourceView source) {
        var body = new ClientReviewSubjectBody();
        body.setPartyId(Long.toString(partyId));
        body.setProfileId(Long.toString(profileId));
        body.setSubjectRole(role);
        body.setQualificationType(qualification);
        if (organization) {
            body.setLegalName(f.organizationLegalName());
            body.setTradeName(f.organizationTradeName());
            body.setIncorporationCountry(f.organizationIncorporationCountry());
            body.setOrganizationType(f.organizationOrganizationType());
            body.setIncorporationDate(text(f.organizationIncorporationDate()));
        } else {
            body.setGivenName(f.individualGivenName());
            body.setMiddleName(f.individualMiddleName());
            body.setFamilyName(f.individualFamilyName());
            body.setBirthDate(text(f.individualBirthDate()));
            body.setNationalityCountry(f.individualNationalityCountry());
            body.setResidenceCountry(f.individualResidenceCountry());
        }
        body.setIdentifierType(f.identifierIdentifierType());
        body.setIssuingCountry(f.identifierIssuingCountry());
        body.setIdentifierVaultToken(f.identifierVaultToken());
        body.setIdentifierMasked(f.identifierMaskedValue());
        var binding = source.getIdentifiers().get(profileId);
        if (binding == null || binding.getIdentityKeyId() == null) {
            throw new ErrorException("提交主体缺少已绑定身份键");
        }
        body.setIdentityKeyId(Long.toString(binding.getIdentityKeyId()));
        body.setValidFrom(text(f.identifierIssueDate()));
        // 本库到期日含当日有效；I01 validTo 不含当日，故加一天。
        body.setValidTo(f.identifierExpiryDate() == null ? null : f.identifierExpiryDate().plusDays(1).toString());
        return body;
    }

    /** 关系。 */
    ClientReviewRelationBody relation(ClientPartyRelation relation) {
        var body = new ClientReviewRelationBody();
        body.setRelationId(Long.toString(relation.relationId()));
        body.setRelatedPartyId(Long.toString(relation.relatedPartyId()));
        body.setRelationType(relation.relationType());
        body.setOwnershipPercentage(plain(relation.ownershipPercentage()));
        body.setUboBasis(relation.uboBasis());
        body.setJobTitle(relation.jobTitle());
        body.setEffectiveFrom(date(relation.effectiveFrom()));
        body.setEffectiveTo(date(relation.effectiveTo()));
        return body;
    }

    /** 材料引用：文件服务地址、摘要与日期。 */
    ClientReviewDocumentBody document(ClientApplicationDocument document) {
        var body = new ClientReviewDocumentBody();
        body.setDocumentId(Long.toString(document.documentId()));
        body.setSubjectPartyId(Long.toString(document.subjectPartyId()));
        body.setRequirementCode(document.requirementCode());
        body.setDocumentType(document.documentType());
        body.setFileAddress(document.fileAddress());
        body.setContentHash(document.contentHash());
        body.setIssueDate(text(document.issueDate()));
        body.setExpiryDate(text(document.expiryDate()));
        return body;
    }

    /** 扩展属性（库内均为非 Vault 级）。 */
    List<ClientReviewAttributeBody> attributes(ClientReviewSourceView source) {
        var partyByProfile = new HashMap<Long, Long>();
        partyByProfile.put(source.getIdentity().profileId(), source.getIdentity().partyId());
        source.getPeople().forEach(person -> partyByProfile.put(person.identity().profileId(), person.identity().partyId()));
        return source.getAttributes().stream().filter(row -> partyByProfile.containsKey(row.getProfileId())).map(row -> {
            var body = new ClientReviewAttributeBody();
            body.setSubjectPartyId(Long.toString(partyByProfile.get(row.getProfileId())));
            body.setAttributeCode(row.getAttributeCode());
            body.setAttributeValue(ClientDraftValues.attributeText(row));
            return body;
        }).toList();
    }

    /** 每种声明的最新一行。 */
    List<ClientReviewDeclarationBody> declarations(ClientReviewSourceView source) {
        var latest = new LinkedHashMap<String, ApplicationDeclaration>();
        source.getDeclarations().forEach(row -> latest.put(row.getDeclarationType(), row));
        var formatter = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        return latest.values().stream().map(row -> {
            var body = new ClientReviewDeclarationBody();
            body.setDeclarationType(row.getDeclarationType());
            body.setDeclaredValue(row.getDeclaredValue());
            body.setDeclaredAt(formatter.format(row.getDeclaredTime()));
            return body;
        }).toList();
    }

    /** 可空 ID 的十进制字符串。 */
    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** 十进制字符串，不使用科学计数法。 */
    static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** UTC 日初的时间点转换为业务日期。 */
    static String date(Instant value) {
        return value == null ? null : LocalDate.ofInstant(value, ZoneOffset.UTC).toString();
    }

    /** 业务日期。 */
    static String text(LocalDate value) {
        return value == null ? null : value.toString();
    }

    /**
     * I03 撤回申请当前绑定的审核案件
     * <li>已是 WITHDRAWN 视为成功（compliance 幂等）；CLIENT_REVIEW_CLOSED 以 FailException 抛出，由调用方按终态处理。</li>
     */
    @Override
    public void withdraw(long caseId, long applicationId, long accountId) {
        var body = new ClientReviewWithdrawalRequestBody();
        body.setApplicationId(Long.toString(applicationId));
        body.setRequestedByAccountId(Long.toString(accountId));
        var response = risk.withdraw(CALLER, Long.toString(caseId), body);
        if (response == null || !Long.toString(caseId).equals(response.getCaseId())) {
            throw new ErrorException("compliance 撤回响应不完整");
        }
    }

    /** I02：同一任务的固定分类集合；Risk 只接受完全相同的重放。 */
    @Override
    public void deliverClassifications(com.paytech.client.master.domain.onboarding.ClientClassificationContext source) {
        var application = source.getApplication();
        var body = new com.paytech.compliance.api.request.ClientReviewClassificationsRequestBody();
        body.setApplicationId(Long.toString(application.getApplicationId()));
        body.setSubmissionId(application.getCurrentSubmissionId());
        body.setConfirmedBy(Long.toString(source.getProfile().getClassificationConfirmedBy()));
        body.setConfirmedAt(new DateTimeFormatterBuilder().appendInstant(6).toFormatter()
            .format(source.getProfile().getClassificationConfirmedTime()));
        body.setClassifications(source.getClassifications().stream().map(row -> {
            var item = new com.paytech.compliance.api.request.ClientReviewClassificationBody();
            item.setIndustryCode(row.getIndustryCode());
            item.setMccCode(row.getMccCode());
            item.setPrimary(row.isPrimary());
            return item;
        }).toList());
        var response = risk.receiveClassifications(CALLER, Long.toString(source.getCaseId()), body);
        if (response == null || !Long.toString(source.getCaseId()).equals(response.getCaseId())) {
            throw new ErrorException("Risk 分类接收响应不完整");
        }
    }
}
