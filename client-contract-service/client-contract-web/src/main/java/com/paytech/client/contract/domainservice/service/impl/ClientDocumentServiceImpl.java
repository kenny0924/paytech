package com.paytech.client.contract.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.contract.core.dal.dataobject.*;
import com.paytech.client.contract.domain.enums.*;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.service.ClientDocumentService;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import com.paytech.risk.api.enums.MaterialQualificationType;
/** 申请行锁保护材料集合；Hash 来自事务前的 Platform 核验，普通保存不生成 BCR。 */
public final class ClientDocumentServiceImpl implements ClientDocumentService {
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final IdGenerator ids;
    private final Clock clock;
    /** 注入同库能力，外部服务不进入此层。 */
    public ClientDocumentServiceImpl(ClientDraftRepository drafts,ClientPeopleRepository people,ClientDocumentRepository documents,IdGenerator ids,Clock clock) {
        this.drafts=drafts;this.people=people;this.documents=documents;this.ids=ids;this.clock=clock;
    }
    /** 原申请归属先于原命令回执；不因引用已删除或申请已终态重新执行。 */
    @Override public DocumentCommandReceiptResponseBody previous(ClientDraftCommand command,long applicationId) {
        application(command.accountId(),applicationId,false);return replay(command,applicationId);
    }
    /** 锁内重新派生主体和国家，不能把旧页面或被移除人员悄悄换到新目标。 */
    @Override public ClientMaterialTarget target(long accountId,long applicationId,long subjectPartyId,boolean lock) {
        var app=application(accountId,applicationId,lock);editable(app);
        boolean organization=app.partyId()==subjectPartyId && app.clientType()==ClientType.ORGANIZATION;
        long profile=app.profileId();MaterialQualificationType qualification=organization?MaterialQualificationType.ORGANIZATION_KYB:MaterialQualificationType.INDIVIDUAL_KYC;
        if (app.partyId()!=subjectPartyId) {
            if (app.clientType()!=ClientType.ORGANIZATION) throw new FailException(PERSON_NOT_FOUND);
            var person=people.person(app,subjectPartyId,lock);
            if (person==null || people.relations(app).stream().noneMatch(relation -> relation.relatedPartyId()==subjectPartyId
                && (relation.status()==PartyRelationStatus.DRAFT || relation.status()==PartyRelationStatus.ACTIVE))) throw new FailException(PERSON_NOT_FOUND);
            profile=person.profileId();qualification=MaterialQualificationType.RELATED_PERSON_KYC;
        }
        String country=documents.country(profile,organization);
        if (country==null || !country.matches("[A-Z]{2}")) throw new FailException(MATERIAL_COUNTRY_REQUIRED);
        return new ClientMaterialTarget(applicationId,app.clientId(),subjectPartyId,qualification,country);
    }
    /** 原申请锁内再次校验本地目标；成功回执与引用共同提交。 */
    @Override public DocumentCommandReceiptResponseBody associate(ClientDraftCommand command,ClientMaterialTarget expected,DocumentReferenceRequestBody request,String hash) {
        requireTransaction();var app=application(command.accountId(),expected.applicationId(),true);
        var previous=replay(command,app.applicationId());if (previous!=null) return previous;
        var current=target(command.accountId(),expected.applicationId(),expected.subjectPartyId(),true);
        if (!expected.equals(current)) throw new FailException(MATERIAL_CONTEXT_CHANGED);
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        var document=new ClientApplicationDocument(ids.nextId(),current.subjectPartyId(),request.requirementCode(),request.documentType(),ClientDraftCommand.id(request.fileId()),hash);
        documents.insert(app.applicationId(),document,now);people.touch(app,now);
        people.receipt(ids.nextId(),command,reference(document),now);
        return receipt(command,app.applicationId(),document,now);
    }
    /** 删除后旧键重放 204，新键访问同一已不存在引用返回 404。 */
    @Override public void remove(ClientDraftCommand command,long applicationId,long documentId) {
        requireTransaction();var app=application(command.accountId(),applicationId,true);
        if (replay(command,applicationId)!=null) return;
        editable(app);var document=documents.document(applicationId,documentId);
        if (document==null) throw new FailException(DOCUMENT_NOT_FOUND);
        if (documents.remove(applicationId,documentId)!=1) throw new FailException(MATERIAL_CONTEXT_CHANGED);
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);people.touch(app,now);people.receipt(ids.nextId(),command,reference(document),now);
    }
    /** 命令操作和请求摘要都绑定原资源，不能挪用到另一个申请。 */
    private DocumentCommandReceiptResponseBody replay(ClientDraftCommand command,long applicationId) {
        var record=drafts.command(command);if (record==null) return null;
        if (!command.hash().equals(record.requestHash())) throw new FailException(IDEMPOTENCY_CONFLICT);
        if (!"SUCCEEDED".equals(record.status()) || record.responseRef()==null || record.completedAt()==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        String[] parts=record.responseRef().split(":",-1);
        if (parts.length!=4 || !"D".equals(parts[0])) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        try { return receipt(command,applicationId,new ClientApplicationDocument(ClientDraftCommand.id(parts[1]),ClientDraftCommand.id(parts[2]),null,null,ClientDraftCommand.id(parts[3]),null),record.completedAt()); }
        catch (FailException invalid) { throw new ErrorException(DRAFT_CONTEXT_INVALID); }
    }
    /** 只保存重放所需固定坐标；D + 三个有符号 BIGINT 最多 61 字符，适配既有 64 字符字段。 */
    private String reference(ClientApplicationDocument document) { return "D:"+document.documentId()+":"+document.subjectPartyId()+":"+document.fileId(); }
    /** 协议回执中的时间始终为首次成功的 UTC 微秒。 */
    private DocumentCommandReceiptResponseBody receipt(ClientDraftCommand command,long app,ClientApplicationDocument document,Instant time) {
        return new DocumentCommandReceiptResponseBody(command.key(),Long.toString(app),Long.toString(document.documentId()),Long.toString(document.subjectPartyId()),Long.toString(document.fileId()),
            "CLIENT_DOCUMENT_ASSOCIATE".equals(command.operation())?"ASSOCIATED":"REMOVED",new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time));
    }
    /** 读取与写入都核验准确原申请，不用最新申请替换。 */
    private ClientDraftIdentity application(long account,long app,boolean lock) { return ClientDraftPolicy.requireValid(drafts.identity(app,account,lock)); }
    /** 终态及审核中不允许更改材料集合。 */
    private void editable(ClientDraftIdentity app) { if (!ClientDraftPolicy.isEditable(app)) throw new FailException(APPLICATION_NOT_EDITABLE); }
    /** 防止进程内误用绕过原子写边界。 */
    private void requireTransaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("材料写入必须由入口事务包裹"); }
}
