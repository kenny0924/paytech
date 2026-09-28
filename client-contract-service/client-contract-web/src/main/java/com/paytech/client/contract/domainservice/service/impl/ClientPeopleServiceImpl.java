package com.paytech.client.contract.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.contract.core.dal.dataobject.*;
import com.paytech.client.contract.domain.enums.*;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.service.ClientPeopleService;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import static com.paytech.client.contract.domain.onboarding.ClientPeopleOperation.*;
/** Processor 提供本地事务；这里在锁定申请后重新核对所有人、状态、关系和原命令。 */
public final class ClientPeopleServiceImpl implements ClientPeopleService {
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final IdGenerator ids;
    private final Clock clock;
    private record Change(long partyId,long relationId) {}
    public ClientPeopleServiceImpl(ClientDraftRepository drafts,ClientPeopleRepository people,IdGenerator ids,Clock clock) {
        this.drafts=drafts;this.people=people;this.ids=ids;this.clock=clock;
    }
    public PeopleCommandReceiptResponseBody createPerson(ClientDraftCommand command,long applicationId,ClientDraftPatch person,ClientRelationValues relation) {
        return execute(command,applicationId,CREATE_PERSON,(application,now) -> {
            person.requirePerson();
            var creation=new ClientPersonCreation(applicationId,ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),now);
            people.create(creation,person);long relationId=ids.nextId();
            people.createRelation(application,relationId,creation.partyId(),relation,now);
            return new Change(creation.partyId(),relationId);
        });
    }
    public PeopleCommandReceiptResponseBody savePerson(ClientDraftCommand command,long applicationId,long partyId,ClientDraftPatch patch) {
        return execute(command,applicationId,SAVE_PERSON,(application,now) -> {
            var person=person(application,partyId);patch.requirePerson();
            if (!ClientDraftPolicy.isPersonEditable(application,person)) throw new FailException(PERSON_NOT_EDITABLE);
            people.savePerson(person,patch,now);return new Change(partyId,0);
        });
    }
    public PeopleCommandReceiptResponseBody createRelation(ClientDraftCommand command,long applicationId,long partyId,ClientRelationValues relation) {
        return execute(command,applicationId,CREATE_RELATION,(application,now) -> {
            person(application,partyId);unique(application,partyId,relation,0);long id=ids.nextId();
            people.createRelation(application,id,partyId,relation,now);return new Change(partyId,id);
        });
    }
    public PeopleCommandReceiptResponseBody saveRelation(ClientDraftCommand command,long applicationId,long relationId,Map<String,String> patch) {
        return execute(command,applicationId,SAVE_RELATION,(application,now) -> {
            var relation=relation(application,relationId);var values=ClientRelationValues.merge(relation,patch);
            unique(application,relation.relatedPartyId(),values,relationId);
            people.saveRelation(application,relationId,values,now);return new Change(relation.relatedPartyId(),relationId);
        });
    }
    public void removeRelation(ClientDraftCommand command,long applicationId,long relationId) {
        execute(command,applicationId,REMOVE_RELATION,(application,now) -> {
            var relation=relation(application,relationId);people.removeRelation(application,relationId);
            return new Change(relation.relatedPartyId(),relationId);
        });
    }
    private PeopleCommandReceiptResponseBody execute(ClientDraftCommand command,long applicationId,ClientPeopleOperation operation,
        BiFunction<ClientDraftIdentity,Instant,Change> action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("人员业务必须由入口事务包裹");
        if (!operation.getCode().equals(command.operation())) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        var application=ClientDraftPolicy.requireValid(drafts.identity(applicationId,command.accountId(),true));
        var replay=replay(command,applicationId,operation);if (replay!=null) return replay;
        if (!ClientDraftPolicy.isEditable(application)) throw new FailException(APPLICATION_NOT_EDITABLE);
        if (application.clientType()!=ClientType.ORGANIZATION) throw new FailException(ORGANIZATION_REQUIRED);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);var change=action.apply(application,now);
        people.touch(application,now);
        people.receipt(ids.nextId(),command,operation.reference()+":"+change.partyId()+":"+change.relationId(),now);
        return receipt(command,applicationId,operation,change,now);
    }
    private ClientPersonIdentity person(ClientDraftIdentity application,long id) {
        var person=people.person(application,id,true);if (person==null) throw new FailException(PERSON_NOT_FOUND);
        return person;
    }
    private ClientPartyRelation relation(ClientDraftIdentity application,long id) {
        var relation=people.relation(application,id);if (relation==null) throw new FailException(RELATION_NOT_FOUND);
        if (relation.status()!=PartyRelationStatus.DRAFT) throw new FailException(RELATION_NOT_EDITABLE);
        // 当前关系必须指向本申请有权使用的人员；不能借关系 ID 访问别的申请草稿。
        person(application,relation.relatedPartyId());return relation;
    }
    private void unique(ClientDraftIdentity application,long partyId,ClientRelationValues relation,long exclude) {
        if (people.duplicate(application.partyId(),partyId,relation.relationType(),exclude)) throw new FailException(RELATION_ALREADY_EXISTS);
    }
    private PeopleCommandReceiptResponseBody replay(ClientDraftCommand command,long applicationId,ClientPeopleOperation operation) {
        var record=drafts.command(command);if (record==null) return null;
        if (!command.hash().equals(record.requestHash())) throw new FailException(IDEMPOTENCY_CONFLICT);
        if (!"SUCCEEDED".equals(record.status()) || record.responseRef()==null || record.completedAt()==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        var parts=record.responseRef().split(":",-1);
        if (parts.length!=3 || !operation.reference().equals(parts[0])) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        try {
            long partyId=ClientDraftCommand.id(parts[1]);
            long relationId=operation==SAVE_PERSON && "0".equals(parts[2])?0:ClientDraftCommand.id(parts[2]);
            if (operation==SAVE_PERSON && relationId!=0) throw new FailException(INVALID_DRAFT);
            return receipt(command,applicationId,operation,new Change(partyId,relationId),record.completedAt());
        } catch (FailException malformed) { throw new ErrorException(DRAFT_CONTEXT_INVALID); }
    }
    private PeopleCommandReceiptResponseBody receipt(ClientDraftCommand command,long applicationId,ClientPeopleOperation operation,Change change,Instant now) {
        return new PeopleCommandReceiptResponseBody(command.key(),Long.toString(applicationId),Long.toString(change.partyId()),
            change.relationId()==0?null:Long.toString(change.relationId()),operation.result(),new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(now));
    }
}
