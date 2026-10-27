package com.paytech.client.master.domainservice.repository;
import java.time.Instant;
import java.util.List;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.onboarding.*;
public interface ClientPeopleRepository {
    List<ClientPersonView> people(ClientDraftIdentity application);
    List<ClientPartyRelation> relations(ClientDraftIdentity application);
    ClientPersonIdentity person(ClientDraftIdentity application,long partyId,boolean lock);
    ClientPartyRelation relation(ClientDraftIdentity application,long relationId);
    boolean duplicate(long organizationId,long partyId,String type,long excludeId);
    /** 新人员资料行及事务外准备的资料列；扩展属性由 PartyAttributeRepository 保存。 */
    void create(ClientPersonCreation creation,ClientDraftChanges changes);
    /** 保存事务外准备的人员资料列。 */
    void savePerson(ClientPersonIdentity person,ClientDraftChanges changes,Instant now);
    /** 人员当前资料（住址与标识）。 */
    ClientDraftFields personFields(ClientDraftIdentity application,ClientPersonIdentity person);
    void createRelation(ClientDraftIdentity application,long id,long partyId,ClientRelationValues values,Instant now);
    void saveRelation(ClientDraftIdentity application,long id,ClientRelationValues values,Instant now);
    void removeRelation(ClientDraftIdentity application,long id);
    void touch(ClientDraftIdentity application,Instant now);
}
