package com.paytech.client.master.ext.rest;

import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.OnboardingCodeResponseBody;
import com.paytech.client.master.core.dal.dataobject.CatalogItem;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientDraftContact;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.onboarding.ClientDraftValues;
import com.paytech.client.master.domain.onboarding.ClientRelationValues;
import com.paytech.client.master.domain.onboardingadmin.ClientAdminDetail;
import com.paytech.client.master.domain.onboardingadmin.ClientAdminView;
import com.paytech.client.master.domain.onboardingadmin.JurisdictionAdminDetail;
import com.paytech.client.master.domain.onboardingadmin.SchemaAdminDetail;

/** 运营入驻查询的响应转换；证件只给脱敏值，联系方式只给脱敏展示值。 */
final class OnboardingAdminConverter {
    /** 仅静态转换。 */
    private OnboardingAdminConverter() {
    }

    /** 辖区包。 */
    static AdminJurisdictionResponseBody jurisdiction(JurisdictionAdminDetail detail) {
        var body = new AdminJurisdictionResponseBody();
        var row = detail.jurisdiction();
        body.setJurisdictionCode(row.getJurisdictionCode());
        body.setCountryCode(row.getCountryCode());
        body.setLabelKey(row.getLabelKey());
        body.setAddressFormatCode(row.getAddressFormatCode());
        body.setStatus(row.getStatus().getCode());
        body.setChangeVersion(row.getChangeVersion());
        body.setRules(detail.rules().stream().map(rule -> {
            var item = new AdminJurisdictionResponseBody.RuleSettingItem();
            item.setScopeType(rule.getScopeType().getCode());
            item.setScopeCode(rule.getScopeCode());
            item.setRuleCode(rule.getRuleCode());
            item.setRuleValue(rule.getRuleValue());
            item.setStatus(rule.getStatus().getCode());
            return item;
        }).toList());
        body.setOrganizationTypes(detail.organizationTypes().stream().map(type -> {
            var item = new AdminJurisdictionResponseBody.OrganizationTypeItem();
            item.setTypeCode(type.getTypeCode());
            item.setLabelKey(type.getLabelKey());
            item.setDisplayOrder(type.getDisplayOrder());
            item.setStatus(type.getStatus().getCode());
            return item;
        }).toList());
        body.setIdentifierTypes(detail.identifierTypes().stream().map(type -> {
            var item = new AdminJurisdictionResponseBody.IdentifierTypeItem();
            item.setIdentifierTypeCode(type.getIdentifierTypeCode());
            item.setJurisdictionCode(type.getJurisdictionCode());
            item.setPartyType(type.getPartyType().getCode());
            item.setLabelKey(type.getLabelKey());
            item.setFormatRegex(type.getFormatRegex());
            item.setNormalizationRule(type.getNormalizationRule());
            item.setChecksumRule(type.getChecksumRule());
            item.setIssuerScopeMode(type.getIssuerScopeMode().getCode());
            item.setStatus(type.getStatus().getCode());
            return item;
        }).toList());
        body.setRelationTypes(detail.relationTypes().stream().map(type -> {
            var item = new AdminJurisdictionResponseBody.RelationTypeItem();
            item.setRelationTypeCode(type.getRelationTypeCode());
            item.setJurisdictionCode(type.getJurisdictionCode());
            item.setRuleRole(type.getRuleRole().getCode());
            item.setUboFlag(Boolean.TRUE.equals(type.getUboFlag()));
            item.setOwnershipPctRequired(Boolean.TRUE.equals(type.getOwnershipPctRequired()));
            item.setLabelKey(type.getLabelKey());
            item.setStatus(type.getStatus().getCode());
            return item;
        }).toList());
        body.setAddressComponents(detail.addressComponents().stream().map(component -> {
            var item = new AdminJurisdictionResponseBody.AddressComponentItem();
            item.setComponent(component.getComponent());
            item.setCatalogCode(component.getCatalogCode());
            item.setRequired(Boolean.TRUE.equals(component.getRequiredFlag()));
            item.setOrder(component.getDisplayOrder());
            return item;
        }).toList());
        return body;
    }

    /** Schema 版本。 */
    static AdminOnboardingSchemaResponseBody schema(SchemaAdminDetail detail) {
        var body = new AdminOnboardingSchemaResponseBody();
        var schema = detail.schema();
        body.setSchemaId(schema.getSchemaId().toString());
        body.setJurisdictionCode(schema.getJurisdictionCode());
        body.setPartyType(schema.getPartyType().getCode());
        body.setRoleType(schema.getRoleType());
        body.setOrganizationTypeCode(schema.getOrganizationTypeCode());
        body.setVersionNo(schema.getVersionNo());
        body.setStatus(schema.getStatus().getCode());
        body.setApprovedTime(time(schema.getApprovedTime()));
        body.setFields(detail.fields().stream().map(field -> {
            var item = new AdminOnboardingSchemaResponseBody.SchemaFieldItem();
            item.setSection(field.getSection().getCode());
            item.setDisplayGroup(field.getDisplayGroup());
            item.setFieldKey(field.getFieldKey());
            item.setWidgetCode(field.getWidgetCode().getCode());
            item.setCatalogCode(field.getCatalogCode());
            item.setRequirementLevel(field.getRequirementLevel().getCode());
            item.setRequirementGroup(field.getRequirementGroup());
            item.setAppliesToSubject(field.getAppliesToSubject().getCode());
            item.setValidationPattern(field.getValidationPattern());
            item.setMinLength(field.getMinLength());
            item.setMaxLength(field.getMaxLength());
            item.setVisibleWhen(field.getVisibleWhen());
            item.setLabelKey(field.getLabelKey());
            item.setHelpKey(field.getHelpKey());
            item.setDisplayOrder(field.getDisplayOrder());
            return item;
        }).toList());
        return body;
    }

    /** 目录项。 */
    static AdminCatalogItemResponseBody catalogItem(CatalogItem row) {
        var body = new AdminCatalogItemResponseBody();
        body.setCatalogCode(row.getCatalogCode());
        body.setItemCode(row.getItemCode());
        body.setJurisdictionCode(row.getJurisdictionCode());
        body.setLabelKey(row.getLabelKey());
        body.setClientTypes(row.getClientTypes());
        body.setDisplayOrder(row.getDisplayOrder());
        body.setStatus(row.getStatus().getCode());
        return body;
    }

    /** Client 列表行。 */
    static AdminClientSummaryResponseBody client(ClientAdminView row) {
        var body = new AdminClientSummaryResponseBody();
        body.setClientId(row.getClientId().toString());
        body.setClientNo(row.getClientNo());
        body.setClientType(row.getClientType().getCode());
        body.setStatus(row.getStatus().getCode());
        body.setJurisdictionCode(row.getJurisdictionCode());
        body.setResponsiblePartnerId(id(row.getResponsiblePartnerId()));
        body.setLegalName(row.getLegalName());
        body.setLatestApplicationId(id(row.getLatestApplicationId()));
        body.setLatestApplicationStatus(row.getLatestApplicationStatus() == null ? null : row.getLatestApplicationStatus().getCode());
        body.setCreatedAt(time(row.getCreateTime()));
        return body;
    }

    /** Client 详情。 */
    static AdminClientDetailResponseBody detail(ClientAdminDetail detail) {
        var body = new AdminClientDetailResponseBody();
        body.setClient(client(detail.summary()));
        body.setApplications(detail.applications().stream().map(OnboardingAdminConverter::application).toList());
        var view = detail.latest();
        if (view == null) {
            body.setPeople(List.of());
            body.setRelations(List.of());
            return body;
        }
        var values = ClientDraftValues.values(view.fields());
        values.remove(view.identity().clientType().getCode().equals("INDIVIDUAL") ? "organization" : "individual");
        var contact = new java.util.LinkedHashMap<String, String>();
        for (ClientDraftContact row : view.contacts()) {
            contact.put(row.contactType().toLowerCase(java.util.Locale.ROOT), row.maskedValue());
        }
        values.put("contact", contact);
        values.put("attributes", ClientDraftValues.attributes(view.attributes(), view.identity().profileId()));
        body.setValues(values);
        body.setPeople(view.people().stream().map(person -> {
            var item = new AdminClientDetailResponseBody.Person();
            item.setPartyId(Long.toString(person.identity().partyId()));
            var personValues = ClientDraftValues.values(person.fields());
            personValues.remove("organization");
            personValues.remove("business");
            personValues.put("attributes", ClientDraftValues.attributes(view.attributes(), person.identity().profileId()));
            item.setValues(personValues);
            return item;
        }).toList());
        body.setRelations(view.relations().stream().map(relation -> {
            var item = new AdminClientDetailResponseBody.Relation();
            item.setRelationId(Long.toString(relation.relationId()));
            item.setRelatedPartyId(Long.toString(relation.relatedPartyId()));
            item.setRelationType(relation.relationType());
            item.setOwnershipPercentage(ClientRelationValues.decimal(relation.ownershipPercentage()));
            item.setUboBasis(relation.uboBasis());
            item.setJobTitle(relation.jobTitle());
            item.setStatus(relation.status().getCode());
            return item;
        }).toList());
        return body;
    }

    /** 申请历史项。 */
    static AdminClientDetailResponseBody.Application application(ClientApplicationSubmission row) {
        var item = new AdminClientDetailResponseBody.Application();
        item.setApplicationId(Long.toString(row.getApplicationId()));
        item.setApplicationNo(row.getApplicationNo());
        item.setStatus(row.getStatus().getCode());
        item.setJurisdictionCode(row.getJurisdictionCode());
        item.setOrganizationTypeCode(row.getOrganizationTypeCode());
        item.setSchemaId(id(row.getSchemaId()));
        item.setResponsiblePartnerId(id(row.getResponsiblePartnerId()));
        item.setSubmissionRound(row.getSubmissionRound());
        item.setDueDiligenceCaseId(id(row.getDueDiligenceCaseId()));
        item.setQualificationDecisionId(id(row.getQualificationDecisionId()));
        item.setSubmittedAt(time(row.getSubmittedTime()));
        item.setUpdatedAt(time(row.getUpdateTime()));
        return item;
    }

    /** 刚签发的入驻码：唯一一次返回明文 code。 */
    static OnboardingCodeResponseBody issuedCode(PartnerOnboardingCode row) {
        var body = code(row);
        body.setCode(row.getCode());
        return body;
    }

    /** 入驻码查询与作废结果：只给掩码（****-末 4 位）。 */
    static OnboardingCodeResponseBody code(PartnerOnboardingCode row) {
        var body = new OnboardingCodeResponseBody();
        body.setCodeId(row.getCodeId().toString());
        body.setCodeMasked("****-" + row.getCode().substring(row.getCode().length() - 4));
        body.setLocalPartnerId(row.getLocalPartnerId().toString());
        body.setStatus(row.getStatus().getCode());
        body.setEffectiveFrom(time(row.getEffectiveFrom()));
        body.setEffectiveTo(time(row.getEffectiveTo()));
        body.setMaxUses(row.getMaxUses());
        body.setUseCount(row.getUseCount());
        body.setNote(row.getNote());
        body.setIssuedBy(id(row.getIssuedBy()));
        body.setIssuedAt(time(row.getCreateTime()));
        body.setRevokedBy(id(row.getRevokedBy()));
        body.setRevokedAt(time(row.getRevokedTime()));
        return body;
    }

    /** 可空 ID。 */
    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** 可空 UTC 时间（ISO-8601，微秒）。 */
    static String time(Instant value) {
        return value == null ? null : new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(value);
    }
}
