package com.paytech.client.master.domainservice.repository;
import java.time.Instant;
import java.util.List;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.enums.PartyRelationType;
import com.paytech.client.master.domain.onboarding.*;
public interface ClientPeopleRepository {
    List<ClientPersonView> people(ClientDraftIdentity application);
    List<ClientPartyRelation> relations(ClientDraftIdentity application);
    ClientPersonIdentity person(ClientDraftIdentity application,long partyId,boolean lock);
    ClientPartyRelation relation(ClientDraftIdentity application,long relationId);
    boolean duplicate(long organizationId,long partyId,PartyRelationType type,long excludeId);
    void create(ClientPersonCreation creation,ClientDraftPatch patch);
    void savePerson(ClientPersonIdentity person,ClientDraftPatch patch,Instant now);
    void createRelation(ClientDraftIdentity application,long id,long partyId,ClientRelationValues values,Instant now);
    void saveRelation(ClientDraftIdentity application,long id,ClientRelationValues values,Instant now);
    void removeRelation(ClientDraftIdentity application,long id);
    void touch(ClientDraftIdentity application,Instant now);
}
