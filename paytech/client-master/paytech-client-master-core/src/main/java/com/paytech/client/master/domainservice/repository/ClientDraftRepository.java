package com.paytech.client.master.domainservice.repository;
import java.time.Instant;
import java.util.*;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domain.enums.ClientType;
/** 当前草稿、本地命令记录与创建审计的同库持久化边界。 */
public interface ClientDraftRepository {
    ClientDraftIdentity identity(long applicationId,long accountId,boolean lock);
    /** O02：不按申请账号过滤的申请身份；调用方必须先核对审核任务归属。 */
    ClientDraftIdentity reviewIdentity(long applicationId);
    /** 活动申请优先的最近 21 项，用额外一项判断显示上限。 */
    List<ClientDraftIdentity> recentApplications(long accountId);
    Long activeApplication(long accountId);
    /** 可重新申请的被拒申请：同账号、同类型、Client 仍为 PENDING 的最近一份；没有为空。 */
    ClientDraftIdentity rejectedApplication(long accountId,ClientType type);
    /** 在被拒或已撤回申请的 Client 下新建 DRAFT 申请，并把资料、人员与关系转给新申请；材料与声明不带入。 */
    void reapply(ClientDraftIdentity rejected,long applicationId,ClientApplicationTerms terms,Instant now);
    ClientDraftFields fields(ClientDraftIdentity identity);
    List<ClientDraftContact> contacts(long profileId);
    void create(ClientDraftCreation creation,String snapshot);
    /** 保存事务外准备好的资料列、联系方式与 Schema 切换；扩展属性由 PartyAttributeRepository 保存。 */
    void save(ClientDraftIdentity identity,ClientDraftChanges changes,Map<String,ClientDraftContact> contacts,Instant now);
    /**
     * 申请资料版本加一
     * <li>由草稿、人员、关系与材料写入在各自写事务末尾调用；声明记录的版本与之不等即失效。</li>
     *
     * @param applicationId 仍可编辑的申请
     */
    void bumpDraftRevision(long applicationId);
}
