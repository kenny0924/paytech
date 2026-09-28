package com.paytech.client.contract.domainservice.repository;
import java.time.Instant;
import java.util.*;
import com.paytech.client.contract.core.dal.dataobject.*;
import com.paytech.client.contract.domain.onboarding.*;
/** 当前草稿、本地命令记录与创建审计的同库持久化边界。 */
public interface ClientDraftRepository {
    ClientDraftIdentity identity(long applicationId,long accountId,boolean lock);
    /** 活动申请优先的最近 21 项，用额外一项判断显示上限。 */
    List<ClientDraftIdentity> recentApplications(long accountId);
    Long activeApplication(long accountId);
    ClientDraftCommandRecord command(ClientDraftCommand command);
    ClientDraftFields fields(ClientDraftIdentity identity);
    List<ClientDraftContact> contacts(long profileId);
    void create(ClientDraftCreation creation,String snapshot);
    void receipt(long id,ClientDraftCommand command,long applicationId,String operation,Instant now);
    void save(ClientDraftIdentity identity,ClientDraftPatch patch,Map<String,ClientDraftContact> contacts,Instant now);
}
