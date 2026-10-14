package com.paytech.client.contract.ext.rest;
import java.time.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.domain.enums.*;
import com.paytech.client.contract.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.contract.domain.onboarding.ClientRelationValues;
import com.paytech.client.contract.domain.onboarding.ClientDraftView;
import com.paytech.client.contract.domain.onboarding.ClientDraftPolicy;
/** 输出仅可见值，不返回 Vault 引用、数据库版本或未经聚合的 Risk/文件状态。 */
public final class ClientDraftConverter {
    private ClientDraftConverter() {}
    /** 将有界本库导航事实转换为无敏感字段的 Owner 响应。 */
    public static com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody start(
        List<com.paytech.client.contract.core.dal.dataobject.ClientDraftIdentity> rows) {
        var formatter=new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        var applications=rows.stream().limit(20).map(row ->
            new com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary(
                Long.toString(row.applicationId()),row.applicationNo(),row.clientType().getCode(),row.status().getCode(),
                row.activeSlot()!=null,formatter.format(row.savedAt()))).toList();
        return new com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody(applications,rows.size()>20);
    }
    public static ClientDraftResponseBody response(ClientDraftView view,Map<String,String> contacts) {
        var row=view.identity();var values=values(view.fields());
        values.remove(row.clientType()==ClientType.INDIVIDUAL?"organization":"individual");
        values.put("contact",contacts);
        var people=view.people().stream().map(person -> {
            var personValues=values(person.fields());personValues.remove("organization");personValues.remove("business");
            return new PersonResponseBody(Long.toString(person.identity().partyId()),ClientDraftPolicy.isPersonEditable(row,person.identity()),personValues);
        }).toList();
        var relations=view.relations().stream().map(relation -> new RelationResponseBody(Long.toString(relation.relationId()),
            Long.toString(relation.relatedPartyId()),relation.relationType().getCode(),ClientRelationValues.decimal(relation.ownershipPercentage()),
            ClientRelationValues.date(relation.effectiveFrom()),ClientRelationValues.date(relation.effectiveTo()),relation.status().getCode(),
            ClientDraftPolicy.isEditable(row) && relation.status()==PartyRelationStatus.DRAFT)).toList();
        return new ClientDraftResponseBody(Long.toString(row.applicationId()),row.applicationNo(),Long.toString(row.clientId()),
            row.clientType().getCode(),Long.toString(row.partyId()),row.status().getCode(),new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(row.savedAt()),
            ClientDraftPolicy.isEditable(row),values,people,relations,view.documents().stream().map(document ->
                new com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentResponseBody(Long.toString(document.documentId()),Long.toString(document.subjectPartyId()),
                    document.requirementCode(),document.documentType(),Long.toString(document.fileId()),document.subjectPartyId()==row.partyId()
                    || view.people().stream().anyMatch(person -> person.identity().partyId()==document.subjectPartyId())
                        && view.relations().stream().anyMatch(relation -> relation.relatedPartyId()==document.subjectPartyId()
                            && (relation.status()==PartyRelationStatus.DRAFT || relation.status()==PartyRelationStatus.ACTIVE)))).toList());
    }
    private static Map<String,Map<String,String>> values(ClientDraftFields fields) {
        Map<String,Map<String,String>> values=new LinkedHashMap<>();
        put(values,"individual","givenName",fields.individualGivenName());
        put(values,"individual","middleName",fields.individualMiddleName());
        put(values,"individual","familyName",fields.individualFamilyName());
        put(values,"individual","birthDate",fields.individualBirthDate());
        put(values,"individual","nationalityCountry",fields.individualNationalityCountry());
        put(values,"individual","residenceCountry",fields.individualResidenceCountry());
        put(values,"organization","legalName",fields.organizationLegalName());
        put(values,"organization","tradeName",fields.organizationTradeName());
        put(values,"organization","incorporationCountry",fields.organizationIncorporationCountry());
        put(values,"organization","organizationType",fields.organizationOrganizationType());
        put(values,"organization","incorporationDate",fields.organizationIncorporationDate());
        put(values,"address","addressLine1",fields.addressAddressLine1());
        put(values,"address","addressLine2",fields.addressAddressLine2());
        put(values,"address","city",fields.addressCity());
        put(values,"address","stateOrProvince",fields.addressStateOrProvince());
        put(values,"address","postalCode",fields.addressPostalCode());
        put(values,"address","countryCode",fields.addressCountryCode());
        put(values,"identifier","identifierType",fields.identifierIdentifierType());
        put(values,"identifier","issuingCountry",fields.identifierIssuingCountry());
        put(values,"identifier","identifierValue",fields.identifierIdentifierValue());
        put(values,"identifier","validFrom",fields.identifierValidFrom());
        put(values,"identifier","validTo",fields.identifierValidTo());
        put(values,"business","businessModelCode",fields.businessBusinessModelCode());
        put(values,"business","websiteUrl",fields.businessWebsiteUrl());
        put(values,"business","businessDescription",fields.businessBusinessDescription());
        put(values,"business","expectedVolume",fields.businessExpectedVolume());
        put(values,"business","expectedTicket",fields.businessExpectedTicket());
        put(values,"business","currency",fields.businessCurrency());
        return values;
    }
    private static void put(Map<String,Map<String,String>> values,String group,String field,Object value) {
        String text=switch(value) {
            case null -> null;
            case Instant time -> time.atOffset(ZoneOffset.UTC).toLocalDate().toString();
            case BigDecimal amount -> amount.stripTrailingZeros().toPlainString();
            default -> value.toString();
        };
        values.computeIfAbsent(group,ignored -> new LinkedHashMap<>()).put(field,text);
    }
}
