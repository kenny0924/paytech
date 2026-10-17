package com.paytech.client.master.domainservice.repository.impl;
import java.time.*;
import java.util.*;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.core.dal.mapper.*;
import com.paytech.client.master.domain.enums.PartyRelationType;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.foundation.common.error.*;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
public final class ClientPeopleRepositoryImpl implements ClientPeopleRepository {
    private final ClientPeopleMapper people;
    private final ClientDraftMapper drafts;
    public ClientPeopleRepositoryImpl(ClientPeopleMapper people,ClientDraftMapper drafts) { this.people=people;this.drafts=drafts; }
    public List<ClientPersonView> people(ClientDraftIdentity application) {
        return people.people(application).stream().map(person -> {
            if (drafts.identifiers(person.profileId())!=1) throw new ErrorException(DRAFT_CONTEXT_INVALID);
            var fields=drafts.fields(person.profileId(),application.clientId(),"RESIDENTIAL");
            if (fields==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
            return new ClientPersonView(person,fields);
        }).toList();
    }
    public List<ClientPartyRelation> relations(ClientDraftIdentity application) { return people.relations(application); }
    public ClientPersonIdentity person(ClientDraftIdentity application,long partyId,boolean lock) { return people.person(application,partyId,lock); }
    public ClientPartyRelation relation(ClientDraftIdentity application,long relationId) { return people.relation(application,relationId); }
    public boolean duplicate(long organizationId,long partyId,PartyRelationType type,long excludeId) { return people.duplicate(organizationId,partyId,type,excludeId); }
    public void create(ClientPersonCreation creation,ClientDraftPatch patch) {
        people.insertParty(creation);people.insertProfile(creation);people.insertIndividual(creation);
        people.insertAddress(creation);people.insertIdentifier(creation);
        savePerson(new ClientPersonIdentity(creation.partyId(),creation.profileId(),creation.applicationId(),null,false),patch,creation.now());
    }
    public void savePerson(ClientPersonIdentity person,ClientDraftPatch patch,Instant now) {
        var changes=patch.databaseValues();
        if (has(changes,"individual")) one(drafts.saveIndividual(person.profileId(),changes,now));
        if (has(changes,"address")) one(drafts.saveAddress(person.profileId(),"RESIDENTIAL",changes,now));
        if (has(changes,"identifier")) {
            if (drafts.identifiers(person.profileId())!=1) throw new FailException(PERSON_NOT_EDITABLE);
            // 退回后改证件：先解绑旧身份键，重提时按新号码重新绑定（同号码复用原键）。
            drafts.unbindIdentifier(person.profileId(),now);
            one(drafts.saveIdentifier(person.profileId(),changes,now));
        }
        one(drafts.touchProfile(person.profileId(),now));
    }
    public void createRelation(ClientDraftIdentity application,long id,long partyId,ClientRelationValues values,Instant now) { people.insertRelation(application,id,partyId,values,now); }
    public void saveRelation(ClientDraftIdentity application,long id,ClientRelationValues values,Instant now) { one(people.saveRelation(application,id,values,now)); }
    public void removeRelation(ClientDraftIdentity application,long id) { one(people.removeRelation(application,id)); }
    public void touch(ClientDraftIdentity application,Instant now) { one(drafts.touchApplication(application.applicationId(),now)); }
    private static boolean has(Map<String,Object> values,String group) { return values.keySet().stream().anyMatch(key -> key.startsWith(group+"_")); }
    private static void one(int count) { if (count!=1) throw new ErrorException(DRAFT_CONTEXT_INVALID); }
}
