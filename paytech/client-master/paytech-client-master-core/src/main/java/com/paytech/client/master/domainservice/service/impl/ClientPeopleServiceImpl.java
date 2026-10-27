package com.paytech.client.master.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import static com.paytech.client.master.domain.onboarding.ClientPeopleOperation.*;
/** Processor 提供本地事务；这里在锁定申请后重新核对所有人、状态和关系；重复到达按业务事实防重。 */
public final class ClientPeopleServiceImpl implements ClientPeopleService {
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final IdGenerator ids;
    private final Clock clock;
    private final RelationTypeRepository relationTypes;
    private final PartyAttributeRepository partyAttributes;
    private record Change(long partyId,long relationId) {}
    public ClientPeopleServiceImpl(ClientDraftRepository drafts,ClientPeopleRepository people,IdGenerator ids,Clock clock,
        RelationTypeRepository relationTypes,PartyAttributeRepository partyAttributes) {
        this.drafts=drafts;this.people=people;this.ids=ids;this.clock=clock;this.relationTypes=relationTypes;
        this.partyAttributes=partyAttributes;
    }
    public PeopleCommandReceiptResponseBody createPerson(ClientDraftCommand command,long applicationId,ClientDraftChanges person,ClientRelationValues relation) {
        return execute(command,applicationId,CREATE_PERSON,(application,now) -> {
            requireCatalog(application,relation);
            var creation=new ClientPersonCreation(applicationId,ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),
                application.jurisdictionCode(),now);
            people.create(creation,person);saveAttributes(creation.profileId(),person,now);long relationId=ids.nextId();
            people.createRelation(application,relationId,creation.partyId(),relation,now);
            return new Change(creation.partyId(),relationId);
        });
    }
    public PeopleCommandReceiptResponseBody savePerson(ClientDraftCommand command,long applicationId,long partyId,ClientDraftChanges changes,
        ClientAgreementPartyUsage usage) {
        return execute(command,applicationId,SAVE_PERSON,(application,now) -> {
            var person=person(application,partyId);
            if (!ClientDraftPolicy.isPersonEditable(application,person,usage)) throw new FailException(PERSON_NOT_EDITABLE);
            people.savePerson(person,changes,now);saveAttributes(person.profileId(),changes,now);return new Change(partyId,0);
        });
    }
    /** 关系类型须为申请辖区 ACTIVE 目录项，持股比例与 UBO 依据按目录适用。 */
    private void requireCatalog(ClientDraftIdentity application,ClientRelationValues relation) {
        var type=relationTypes.findForJurisdiction(application.jurisdictionCode()).stream()
            .filter(row -> row.getRelationTypeCode().equals(relation.relationType()) && row.getStatus()==CatalogStatus.ACTIVE)
            .findFirst().orElse(null);
        relation.requireCatalog(type);
    }
    /** 扩展属性逐项写入或清空。 */
    private void saveAttributes(long profileId,ClientDraftChanges changes,Instant now) {
        changes.getAttributes().forEach((code,value) -> {
            if (value==null) {
                partyAttributes.delete(profileId,code);
                return;
            }
            var row=new PartyAttribute();
            row.setAttributeId(ids.nextId());row.setProfileId(profileId);row.setAttributeCode(code);
            row.setValueText(value.text());row.setValueDate(value.date());row.setValueDecimal(value.decimal());
            row.setCreateTime(now);row.setUpdateTime(now);
            partyAttributes.save(row);
        });
    }
    public PeopleCommandReceiptResponseBody createRelation(ClientDraftCommand command,long applicationId,long partyId,ClientRelationValues relation) {
        return execute(command,applicationId,CREATE_RELATION,(application,now) -> {
            person(application,partyId);requireCatalog(application,relation);
            var same=sameRelation(application,partyId,relation);
            if (same!=null) {
                return new Change(partyId,same.relationId());
            }
            unique(application,partyId,relation,0);long id=ids.nextId();
            people.createRelation(application,id,partyId,relation,now);return new Change(partyId,id);
        });
    }
    public PeopleCommandReceiptResponseBody saveRelation(ClientDraftCommand command,long applicationId,long relationId,Map<String,String> patch) {
        return execute(command,applicationId,SAVE_RELATION,(application,now) -> {
            var relation=relation(application,relationId);var values=ClientRelationValues.merge(relation,patch);
            requireCatalog(application,values);
            unique(application,relation.relatedPartyId(),values,relationId);
            people.saveRelation(application,relationId,values,now);return new Change(relation.relatedPartyId(),relationId);
        });
    }
    public void removeRelation(ClientDraftCommand command,long applicationId,long relationId) {
        execute(command,applicationId,REMOVE_RELATION,(application,now) -> {
            if (people.relation(application,relationId)==null) {
                // 本申请下已不存在：重复删除视为完成，不改动申请。
                return null;
            }
            var relation=relation(application,relationId);people.removeRelation(application,relationId);
            return new Change(relation.relatedPartyId(),relationId);
        });
    }
    private PeopleCommandReceiptResponseBody execute(ClientDraftCommand command,long applicationId,ClientPeopleOperation operation,
        BiFunction<ClientDraftIdentity,Instant,Change> action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("人员业务必须由入口事务包裹");
        if (!operation.getCode().equals(command.operation())) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        var application=ClientDraftPolicy.requireValid(drafts.identity(applicationId,command.accountId(),true));
        if (!ClientDraftPolicy.isEditable(application)) throw new FailException(APPLICATION_NOT_EDITABLE);
        if (application.clientType()!=ClientType.ORGANIZATION) throw new FailException(ORGANIZATION_REQUIRED);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);var change=action.apply(application,now);
        if (change==null) {
            return null;
        }
        people.touch(application,now);
        return receipt(applicationId,operation,change,now);
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
    /**
     * 同一申请下已有相同组织、人员和类型的草稿关系且内容相同时返回该关系
     * <li>内容不同仍由 unique 拒绝为 RELATION_ALREADY_EXISTS。</li>
     */
    private ClientPartyRelation sameRelation(ClientDraftIdentity application,long partyId,ClientRelationValues relation) {
        return people.relations(application).stream()
            .filter(existing -> existing.relatedPartyId()==partyId && existing.relationType().equals(relation.relationType()))
            .filter(existing -> existing.status()==PartyRelationStatus.DRAFT && relation.matches(existing))
            .findFirst().orElse(null);
    }
    /** commandId 已停用，恒为空；请求幂等由 Gateway 处理。 */
    private PeopleCommandReceiptResponseBody receipt(long applicationId,ClientPeopleOperation operation,Change change,Instant now) {
        return new PeopleCommandReceiptResponseBody(null,Long.toString(applicationId),Long.toString(change.partyId()),
            change.relationId()==0?null:Long.toString(change.relationId()),operation.result(),new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(now));
    }
}
