package com.paytech.client.master.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.ClientProjectionSnapshot;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** CCT 业务规则和原子写集合；事务由 Processor 包裹，内部只访问本库。 */
public final class ClientDraftServiceImpl implements ClientDraftService {
    private final ClientDraftRepository repository;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final IdGenerator ids;
    private final Clock clock;
    private final CompensationEventRegistrar events;
    public ClientDraftServiceImpl(ClientDraftRepository repository,ClientPeopleRepository people,ClientDocumentRepository documents,IdGenerator ids,Clock clock,CompensationEventRegistrar events) {
        this.repository=repository;this.people=people;this.documents=documents;this.ids=ids;this.clock=clock;this.events=events;
    }
    public CommandReceiptResponseBody create(ClientDraftCommand command,ClientType type) {
        requireTransaction();
        // 已有未完成申请时恢复原申请：同一请求重复到达也落在这里。
        Long existing=repository.activeApplication(command.accountId());
        if (existing!=null) {
            var row=identity(existing,command.accountId(),true);
            if (row.activeSlot()==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
            if (row.clientType()!=type) throw new FailException(ACTIVE_APPLICATION_TYPE_MISMATCH);
            return receipt(row,"RESTORED",now());
        }
        var rejected=repository.rejectedApplication(command.accountId(),type);
        if (rejected!=null) {
            return reapply(command,rejected);
        }
        Instant now=now();
        var creation=new ClientDraftCreation(ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),
            ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),command.accountId(),type,now);
        var history=new ClientProjectionHistory(creation.changeId(),creation.historyId(),Long.toString(creation.clientId()),1,
            BusinessChangeOperation.CREATE,BusinessChangeSnapshotFormat.ENTITY_NO,null,command.accountId(),now,
            creation.clientId(),creation.partyId(),creation.clientNo(),command.accountId(),1);
        String snapshot=ClientProjectionSnapshot.snapshot(history,ClientStatus.PENDING,
            Long.toString(creation.commandRecordId()),"CLIENT_CREATED");
        repository.create(creation,snapshot);
        events.register(ClientTenantProjectionHandler.buildEvent(creation.changeId()));
        return receipt(identity(creation.applicationId(),command.accountId(),false),"CREATED",now);
    }
    /** 原账号条件由同库查询执行，返回前验证每个身份绑定完整。 */
    public List<ClientDraftIdentity> recentApplications(long accountId) {
        return repository.recentApplications(accountId).stream().map(ClientDraftPolicy::requireValid).toList();
    }
    public ClientDraftView read(long accountId,long applicationId) {
        requireTransaction();
        return view(identity(applicationId,accountId,false),applicationId);
    }
    public ClientDraftView readForReview(long applicationId) {
        requireTransaction();
        return view(ClientDraftPolicy.requireValid(repository.reviewIdentity(applicationId)),applicationId);
    }
    private ClientDraftView view(ClientDraftIdentity row,long applicationId) {
        var persons=row.clientType()==ClientType.ORGANIZATION?people.people(row):List.<ClientPersonView>of();
        var relations=row.clientType()==ClientType.ORGANIZATION?people.relations(row):List.<ClientPartyRelation>of();
        var visibleIds=persons.stream().map(person -> person.identity().partyId()).collect(java.util.stream.Collectors.toSet());
        if (relations.stream().anyMatch(relation -> !visibleIds.contains(relation.relatedPartyId()))) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        return new ClientDraftView(row,repository.fields(row),repository.contacts(row.profileId()),persons,relations,documents.documents(applicationId));
    }
    public void prepareSave(ClientDraftCommand command,long applicationId,ClientDraftPatch patch) {
        requireTransaction();
        var row=identity(applicationId,command.accountId(),false);
        editable(row);patch.requireType(row.clientType());
    }
    public CommandReceiptResponseBody save(ClientDraftCommand command,long applicationId,ClientDraftPatch patch,
        Map<String,ClientDraftContact> contacts) {
        requireTransaction();
        var row=identity(applicationId,command.accountId(),true);
        editable(row);patch.requireType(row.clientType());
        Instant now=now();
        repository.save(row,patch,contacts,now);
        return receipt(row,"SAVED",now);
    }
    /** 同类型被拒后重新申请：沿用原 Client、Party 与工作区，不新建 Client，也不再投影 Platform。 */
    private CommandReceiptResponseBody reapply(ClientDraftCommand command,ClientDraftIdentity rejected) {
        Instant now=now();
        long applicationId=ids.nextId();
        repository.reapply(rejected,applicationId,now);
        return receipt(identity(applicationId,command.accountId(),false),"CREATED",now);
    }
    private ClientDraftIdentity identity(long applicationId,long accountId,boolean lock) {
        return ClientDraftPolicy.requireValid(repository.identity(applicationId,accountId,lock));
    }
    private void editable(ClientDraftIdentity row) { if (!ClientDraftPolicy.isEditable(row)) throw new FailException(APPLICATION_NOT_EDITABLE); }
    /** commandId 已停用，恒为空；请求幂等由 Gateway 处理。 */
    private CommandReceiptResponseBody receipt(ClientDraftIdentity row,String operation,Instant time) {
        return new CommandReceiptResponseBody(null,Long.toString(row.applicationId()),row.applicationNo(),Long.toString(row.clientId()),
            row.clientType().getCode(),operation,new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time));
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("草稿业务必须由入口事务包裹");
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
