package com.paytech.client.master.core.dal.mapper;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.onboarding.*;
/** 草稿 SQL 只在 Mapper XML；调用方负责本地事务。 */
public interface ClientDraftMapper {
    ClientDraftIdentity identity(@Param("applicationId") long applicationId, @Param("accountId") long accountId, @Param("lock") boolean lock);
    ClientDraftIdentity reviewIdentity(@Param("applicationId") long applicationId);
    /** 活动申请优先的最近 21 项，用额外一项判断显示上限。 */
    List<ClientDraftIdentity> recentApplications(long accountId);
    Long activeApplication(long accountId);
    /** 本账号最近一份同类型、已被拒绝且 Client 仍为 PENDING 的申请；没有为空。 */
    ClientDraftIdentity rejectedApplication(@Param("accountId") long accountId, @Param("clientType") String clientType);
    /** 在被拒申请的同一 Client 下新建 DRAFT 申请，沿用资格类型。 */
    int insertReapplication(@Param("applicationId") long applicationId, @Param("applicationNo") String applicationNo,
        @Param("rejectedId") long rejectedId, @Param("now") Instant now);
    /** 主体与关联人员的未批准资料改由新申请编辑。 */
    int moveDraftProfiles(@Param("rejectedId") long rejectedId, @Param("applicationId") long applicationId, @Param("now") Instant now);
    /** 草稿关系改由新申请维护；关系按组织、人员、类型唯一，只能转移不能复制。 */
    int moveDraftRelations(@Param("rejectedId") long rejectedId, @Param("applicationId") long applicationId, @Param("now") Instant now);
    ClientDraftFields fields(@Param("profileId") long profileId, @Param("clientId") long clientId, @Param("addressType") String addressType);
    List<ClientDraftContact> contacts(long profileId);
    int identifiers(long profileId);
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
    int saveIndividual(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveOrganization(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveAddress(@Param("profileId") long profileId, @Param("addressType") String addressType, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    /** 解除证件行的永久身份键绑定；旧身份键保留原 Party 归属，重提时重新绑定。 */
    int unbindIdentifier(@Param("profileId") long profileId, @Param("now") Instant now);
    int saveIdentifier(@Param("profileId") long profileId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    int saveBusiness(@Param("clientId") long clientId, @Param("changes") Map<String,Object> changes, @Param("now") Instant now);
    void saveContact(@Param("id") long id, @Param("profileId") long profileId, @Param("contact") ClientDraftContact contact, @Param("now") Instant now);
    void clearContact(@Param("profileId") long profileId, @Param("contactType") String contactType);
    int touchApplication(@Param("applicationId") long applicationId, @Param("now") Instant now);
    int touchProfile(@Param("profileId") long profileId, @Param("now") Instant now);
}
