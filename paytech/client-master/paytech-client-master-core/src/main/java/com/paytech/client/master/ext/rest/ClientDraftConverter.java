package com.paytech.client.master.ext.rest;
import java.time.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.core.dal.dataobject.ClientDraftFields;
import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.master.domain.onboarding.ClientRelationValues;
import com.paytech.client.master.domain.onboarding.ClientDraftView;
import com.paytech.client.master.domain.onboarding.ClientDraftValues;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
/** 输出仅可见值：证件只给类型、签发国、末 4 位脱敏、检查结论与日期，不返回 Vault 引用、号码原值或数据库版本。 */
public final class ClientDraftConverter {
    private ClientDraftConverter() {}
    /** 将有界本库导航事实转换为无敏感字段的 Owner 响应。 */
    public static com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody start(
        List<com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity> rows) {
        var formatter=new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        var applications=rows.stream().limit(20).map(row ->
            new com.paytech.client.contract.api.onboarding.ClientDraftModels.ApplicationSummary(
                Long.toString(row.applicationId()),row.applicationNo(),row.clientType().getCode(),row.status().getCode(),
                row.activeSlot()!=null,formatter.format(row.savedAt()))).toList();
        return new com.paytech.client.contract.api.onboarding.ClientDraftModels.StartResponseBody(applications,rows.size()>20);
    }
    /**
     * 申请资料响应；人员可编辑标记同时考虑 commercial 的合同引用结果。
     *
     * @param view 本库快照
     * @param contacts 已 Reveal 的联系方式
     * @param usage 合同引用结果；无法确认时为失败关闭结果
     * @return 申请资料响应
     */
    public static ClientDraftResponseBody response(ClientDraftView view,Map<String,String> contacts,ClientAgreementPartyUsage usage) {
        var row=view.identity();var values=ClientDraftValues.values(view.fields());
        values.remove(row.clientType()==ClientType.INDIVIDUAL?"organization":"individual");
        values.put("contact",contacts);
        values.put("attributes",ClientDraftValues.attributes(view.attributes(),row.profileId()));
        var people=view.people().stream().map(person -> {
            var personValues=ClientDraftValues.values(person.fields());personValues.remove("organization");personValues.remove("business");
            personValues.put("attributes",ClientDraftValues.attributes(view.attributes(),person.identity().profileId()));
            return new PersonResponseBody(Long.toString(person.identity().partyId()),ClientDraftPolicy.isPersonEditable(row,person.identity(),usage),personValues);
        }).toList();
        var relations=view.relations().stream().map(relation -> new RelationResponseBody(Long.toString(relation.relationId()),
            Long.toString(relation.relatedPartyId()),relation.relationType(),ClientRelationValues.decimal(relation.ownershipPercentage()),
            ClientRelationValues.date(relation.effectiveFrom()),ClientRelationValues.date(relation.effectiveTo()),relation.status().getCode(),
            ClientDraftPolicy.isEditable(row) && relation.status()==PartyRelationStatus.DRAFT,relation.uboBasis(),relation.jobTitle(),
            relation.nomineeFlag(),relation.controlNature())).toList();
        var formatter=new DateTimeFormatterBuilder().appendInstant(6).toFormatter();
        return new ClientDraftResponseBody(Long.toString(row.applicationId()),row.applicationNo(),Long.toString(row.clientId()),
            row.clientType().getCode(),Long.toString(row.partyId()),row.status().getCode(),formatter.format(row.savedAt()),
            ClientDraftPolicy.isEditable(row),values,people,relations,view.documents().stream().map(document ->
                new com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentResponseBody(Long.toString(document.documentId()),Long.toString(document.subjectPartyId()),
                    document.requirementCode(),document.documentType(),document.fileAddress(),document.subjectPartyId()==row.partyId()
                    || view.people().stream().anyMatch(person -> person.identity().partyId()==document.subjectPartyId())
                        && view.relations().stream().anyMatch(relation -> relation.relatedPartyId()==document.subjectPartyId()
                            && (relation.status()==PartyRelationStatus.DRAFT || relation.status()==PartyRelationStatus.ACTIVE)),
                    text(document.issueDate()),text(document.expiryDate()))).toList(),
            row.jurisdictionCode(),row.organizationTypeCode(),row.schemaId()==null?null:row.schemaId().toString(),
            row.reviewDueTime()==null?null:formatter.format(row.reviewDueTime()),
            row.responsiblePartnerId()==null?null:row.responsiblePartnerId().toString(),view.responsiblePartnerName(),
            view.declarations().stream().map(declaration -> declaration(declaration,formatter)).toList());
    }
    /** 一条声明的读回。 */
    private static DeclarationResponseBody declaration(ApplicationDeclaration row,java.time.format.DateTimeFormatter formatter) {
        var body=new DeclarationResponseBody();
        body.setDeclarationId(row.getDeclarationId().toString());
        body.setApplicationId(row.getClientApplicationId().toString());
        body.setDeclarationType(row.getDeclarationType());
        body.setDeclaredValue(row.getDeclaredValue());
        body.setTermsVersion(row.getTermsVersion());
        body.setDeclaredAt(formatter.format(row.getDeclaredTime()));
        return body;
    }
    private static String text(LocalDate value) { return value==null?null:value.toString(); }
}
