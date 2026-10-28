package com.paytech.client.master.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientDocumentModels.*;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.ClientDocumentService;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
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
    /** 锁内重新派生主体和资格类型，不能把旧页面或被移除人员悄悄换到新目标。 */
    @Override public ClientMaterialTarget target(long accountId,long applicationId,long subjectPartyId,boolean lock) {
        var app=application(accountId,applicationId,lock);editable(app);
        if (app.partyId()!=subjectPartyId) {
            if (app.clientType()!=ClientType.ORGANIZATION) throw new FailException(PERSON_NOT_FOUND);
            var person=people.person(app,subjectPartyId,lock);
            if (person==null || people.relations(app).stream().noneMatch(relation -> relation.relatedPartyId()==subjectPartyId
                && (relation.status()==PartyRelationStatus.DRAFT || relation.status()==PartyRelationStatus.ACTIVE))) throw new FailException(PERSON_NOT_FOUND);
        }
        return new ClientMaterialTarget(applicationId,app.clientId(),subjectPartyId,app.partyId()==subjectPartyId,app.jurisdictionCode(),
            app.clientType(),app.responsiblePartnerId());
    }
    /** 原申请锁内再次校验本地目标；同一申请、主体、要求、类型和文件已关联时返回原引用，否则新增并使资料版本加一。 */
    @Override public DocumentCommandReceiptResponseBody associate(ClientDraftCommand command,ClientMaterialTarget expected,DocumentReferenceRequestBody request,String hash) {
        requireTransaction();var app=application(command.accountId(),expected.applicationId(),true);
        var current=target(command.accountId(),expected.applicationId(),expected.subjectPartyId(),true);
        if (!expected.equals(current)) throw new FailException(MATERIAL_CONTEXT_CHANGED);
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        String fileAddress=ClientDraftCommand.fileAddress(request.fileAddress()).value();
        var same=sameDocument(app.applicationId(),current.subjectPartyId(),request,fileAddress);
        if (same!=null) {
            return receipt(command,app.applicationId(),same,now);
        }
        var document=new ClientApplicationDocument(ids.nextId(),current.subjectPartyId(),request.requirementCode(),request.documentType(),fileAddress,hash,
            date(request.issueDate()),date(request.expiryDate()));
        documents.insert(app.applicationId(),document,now);people.touch(app,now);drafts.bumpDraftRevision(app.applicationId());
        return receipt(command,app.applicationId(),document,now);
    }
    /** 本申请下已不存在的引用视为已删除，不改动申请；实际删除时资料版本加一。 */
    @Override public void remove(ClientDraftCommand command,long applicationId,long documentId) {
        requireTransaction();var app=application(command.accountId(),applicationId,true);
        editable(app);var document=documents.document(applicationId,documentId);
        if (document==null) {
            return;
        }
        if (documents.remove(applicationId,documentId)!=1) throw new FailException(MATERIAL_CONTEXT_CHANGED);
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);people.touch(app,now);drafts.bumpDraftRevision(applicationId);
    }
    /** 已由入口校验格式的可空日期。 */
    private static java.time.LocalDate date(String value) { return value==null?null:java.time.LocalDate.parse(value); }
    /** 当前申请下相同主体、要求、类型和文件的已有引用；没有为空。 */
    private ClientApplicationDocument sameDocument(long applicationId,long subjectPartyId,DocumentReferenceRequestBody request,String fileAddress) {
        return documents.documents(applicationId).stream()
            .filter(document -> document.subjectPartyId()==subjectPartyId && fileAddress.equals(document.fileAddress())
                && request.requirementCode().equals(document.requirementCode()) && request.documentType().equals(document.documentType()))
            .findFirst().orElse(null);
    }
    /** 协议回执中的时间为本次处理的 UTC 微秒；commandId 已停用，恒为空。 */
    private DocumentCommandReceiptResponseBody receipt(ClientDraftCommand command,long app,ClientApplicationDocument document,Instant time) {
        return new DocumentCommandReceiptResponseBody(null,Long.toString(app),Long.toString(document.documentId()),Long.toString(document.subjectPartyId()),document.fileAddress(),
            "CLIENT_DOCUMENT_ASSOCIATE".equals(command.operation())?"ASSOCIATED":"REMOVED",new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time));
    }
    /** 读取与写入都核验准确原申请，不用最新申请替换。 */
    private ClientDraftIdentity application(long account,long app,boolean lock) { return ClientDraftPolicy.requireValid(drafts.identity(app,account,lock)); }
    /** 终态及审核中不允许更改材料集合。 */
    private void editable(ClientDraftIdentity app) { if (!ClientDraftPolicy.isEditable(app)) throw new FailException(APPLICATION_NOT_EDITABLE); }
    /** 防止进程内误用绕过原子写边界。 */
    private void requireTransaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("材料写入必须由入口事务包裹"); }
}
