package com.paytech.client.master.core.dal.mapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domain.enums.PartyRelationType;
/** 本库 SQL，申请范围和组织范围必须同时命中。 */
public interface ClientPeopleMapper {
    List<ClientPersonIdentity> people(@Param("application") ClientDraftIdentity application);
    ClientPersonIdentity person(@Param("application") ClientDraftIdentity application,@Param("partyId") long partyId,@Param("lock") boolean lock);
    List<ClientPartyRelation> relations(@Param("application") ClientDraftIdentity application);
    ClientPartyRelation relation(@Param("application") ClientDraftIdentity application,@Param("relationId") long relationId);
    boolean duplicate(@Param("organizationId") long organizationId,@Param("partyId") long partyId,
        @Param("type") PartyRelationType type,@Param("excludeId") long excludeId);
    void insertParty(ClientPersonCreation creation);
    void insertProfile(ClientPersonCreation creation);
    void insertIndividual(ClientPersonCreation creation);
    void insertAddress(ClientPersonCreation creation);
    void insertIdentifier(ClientPersonCreation creation);
    void insertRelation(@Param("application") ClientDraftIdentity application,@Param("id") long id,@Param("partyId") long partyId,
        @Param("values") ClientRelationValues values,@Param("now") Instant now);
    int saveRelation(@Param("application") ClientDraftIdentity application,@Param("id") long id,
        @Param("values") ClientRelationValues values,@Param("now") Instant now);
    int removeRelation(@Param("application") ClientDraftIdentity application,@Param("id") long id);
}
