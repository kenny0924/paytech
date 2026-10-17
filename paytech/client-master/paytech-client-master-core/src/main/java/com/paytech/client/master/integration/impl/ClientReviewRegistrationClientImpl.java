package com.paytech.client.master.integration.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientReviewSourceView;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.compliance.request.ClientReviewBusinessBody;
import com.paytech.risk.api.compliance.request.ClientReviewDocumentBody;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewRelationBody;
import com.paytech.risk.api.compliance.request.ClientReviewSubjectBody;

import lombok.AllArgsConstructor;

/** 把 CCT 提交事实转换为 Risk I01 请求；不在本地保存请求或响应。 */
@AllArgsConstructor
public class ClientReviewRegistrationClientImpl implements ClientReviewRegistrationClient {
    private static final String CALLER = "client-contract-service";

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

    /** 组装完整受审事实；主主体在前。 */
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

    /** 单个主体资料及其已绑定身份键。 */
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
        body.setAddressLine1(f.addressAddressLine1());
        body.setAddressLine2(f.addressAddressLine2());
        body.setCity(f.addressCity());
        body.setStateOrProvince(f.addressStateOrProvince());
        body.setPostalCode(f.addressPostalCode());
        body.setAddressCountry(f.addressCountryCode());
        body.setIdentifierType(f.identifierIdentifierType());
        body.setIssuingCountry(f.identifierIssuingCountry());
        body.setIdentifierValue(f.identifierIdentifierValue());
        var binding = source.getIdentifiers().get(profileId);
        if (binding == null || binding.getIdentityKeyId() == null) {
            throw new ErrorException("提交主体缺少已绑定身份键");
        }
        body.setIdentityKeyId(Long.toString(binding.getIdentityKeyId()));
        body.setValidFrom(date(f.identifierValidFrom()));
        body.setValidTo(date(f.identifierValidTo()));
        return body;
    }

    /** 关系。 */
    ClientReviewRelationBody relation(ClientPartyRelation relation) {
        var body = new ClientReviewRelationBody();
        body.setRelationId(Long.toString(relation.relationId()));
        body.setRelatedPartyId(Long.toString(relation.relatedPartyId()));
        body.setRelationType(relation.relationType().getCode());
        body.setOwnershipPercentage(plain(relation.ownershipPercentage()));
        body.setEffectiveFrom(date(relation.effectiveFrom()));
        body.setEffectiveTo(date(relation.effectiveTo()));
        return body;
    }

    /** 材料引用。 */
    ClientReviewDocumentBody document(ClientApplicationDocument document) {
        var body = new ClientReviewDocumentBody();
        body.setDocumentId(Long.toString(document.documentId()));
        body.setSubjectPartyId(Long.toString(document.subjectPartyId()));
        body.setRequirementCode(document.requirementCode());
        body.setDocumentType(document.documentType());
        body.setFileId(Long.toString(document.fileId()));
        body.setContentHash(document.contentHash());
        return body;
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

    /** I02：同一任务的固定分类集合；Risk 只接受完全相同的重放。 */
    @Override
    public void deliverClassifications(com.paytech.client.master.domain.onboarding.ClientClassificationContext source) {
        var application = source.getApplication();
        var body = new com.paytech.risk.api.compliance.request.ClientReviewClassificationsRequestBody();
        body.setApplicationId(Long.toString(application.getApplicationId()));
        body.setSubmissionId(application.getCurrentSubmissionId());
        body.setConfirmedBy(Long.toString(source.getProfile().getClassificationConfirmedBy()));
        body.setConfirmedAt(new DateTimeFormatterBuilder().appendInstant(6).toFormatter()
            .format(source.getProfile().getClassificationConfirmedTime()));
        body.setClassifications(source.getClassifications().stream().map(row -> {
            var item = new com.paytech.risk.api.compliance.request.ClientReviewClassificationBody();
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
