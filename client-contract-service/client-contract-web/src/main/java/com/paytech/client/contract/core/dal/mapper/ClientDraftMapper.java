package com.paytech.client.contract.core.dal.mapper;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import com.paytech.client.contract.core.dal.dataobject.*;
import com.paytech.client.contract.domain.onboarding.*;
/** 草稿 SQL 只在 Mapper XML；调用方负责本地事务。 */
public interface ClientDraftMapper {
    ClientDraftIdentity identity(@Param("applicationId") long applicationId, @Param("accountId") long accountId, @Param("lock") boolean lock);
    /** 活动申请优先的最近 21 项，用额外一项判断显示上限。 */
    List<ClientDraftIdentity> recentApplications(long accountId);
    Long activeApplication(long accountId);
    ClientDraftCommandRecord command(@Param("command") ClientDraftCommand command);
    ClientDraftFields fields(@Param("profileId") long profileId, @Param("clientId") long clientId, @Param("addressType") String addressType);
    List<ClientDraftContact> contacts(long profileId);
    int identifiers(long profileId);
    int boundIdentifiers(long profileId);
    void insertParty(ClientDraftCreation row);
    void insertClient(ClientDraftCreation row);
    void insertApplication(ClientDraftCreation row);
    void insertProfile(ClientDraftCreation row);
    void insertIndividual(ClientDraftCreation row);
    void insertOrganization(ClientDraftCreation row);
    void insertBusiness(ClientDraftCreation row);
    void insertAddress(ClientDraftCreation row);
    void insertIdentifier(ClientDraftCreation row);
    void insertHistoryRoot(ClientDraftCreation row);
    void insertHistory(@Param("row") ClientDraftCreation row, @Param("snapshot") String snapshot);
    void insertReceipt(@Param("id") long id, @Param("command") ClientDraftCommand command,
        @Param("responseRef") String responseRef, @Param("now") Instant now, @Param("expiry") Instant expiry);
    int saveIndividual(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveOrganization(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveAddress(@Param("profileId") long profileId, @Param("addressType") String addressType, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveIdentifier(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveBusiness(@Param("clientId") long clientId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    void saveContact(@Param("id") long id, @Param("profileId") long profileId, @Param("contact") ClientDraftContact contact, @Param("now") Instant now);
    void clearContact(@Param("profileId") long profileId, @Param("contactType") String contactType);
    int touchApplication(@Param("applicationId") long applicationId, @Param("now") Instant now);
    int touchProfile(@Param("profileId") long profileId, @Param("now") Instant now);
}
