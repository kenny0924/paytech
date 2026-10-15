package com.paytech.client.contract.domainservice.repository.impl;
import java.time.*;
import java.util.*;
import com.paytech.client.contract.core.dal.mapper.ClientDraftMapper;
import com.paytech.client.contract.core.dal.dataobject.*;
import com.paytech.client.contract.domain.onboarding.*;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domainservice.repository.ClientDraftRepository;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
/** 只含 Mapper 调用；不读其他数据库、不调用外部服务。 */
public final class ClientDraftRepositoryImpl implements ClientDraftRepository {
    private final ClientDraftMapper mapper;
    private final IdGenerator ids;
    public ClientDraftRepositoryImpl(ClientDraftMapper mapper,IdGenerator ids) { this.mapper=mapper;this.ids=ids; }
    public ClientDraftIdentity identity(long id,long account,boolean lock) { return mapper.identity(id,account,lock); }
    @Override
    public ClientDraftIdentity reviewIdentity(long id) { return mapper.reviewIdentity(id); }
    /** 仅读取本账号摘要来源，不创建工作区或执行恢复。 */
    public List<ClientDraftIdentity> recentApplications(long accountId) { return mapper.recentApplications(accountId); }
    public Long activeApplication(long account) { return mapper.activeApplication(account); }
    /** 只查本账号同类型、Client 未激活的最近一份被拒申请。 */
    public ClientDraftIdentity rejectedApplication(long account,ClientType type) { return mapper.rejectedApplication(account,type.getCode()); }
    /**
     * 复用原 Client 重新申请
     * <li>主体资料必须仍归被拒申请编辑，否则数据不一致；新申请沿用同一 Party，重提时复用已有身份键。</li>
     * <li>材料文件在 Platform 按申请归属，不带入，由客户在新申请重新上传。</li>
     */
    public void reapply(ClientDraftIdentity rejected,long applicationId,Instant now) {
        if (!Long.valueOf(rejected.applicationId()).equals(rejected.draftApplicationId())) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        one(mapper.insertReapplication(applicationId,"CA-"+applicationId,rejected.applicationId(),now));
        if (mapper.moveDraftProfiles(rejected.applicationId(),applicationId,now)<1) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        mapper.moveDraftRelations(rejected.applicationId(),applicationId,now);
    }
    public ClientDraftFields fields(ClientDraftIdentity row) {
        if (mapper.identifiers(row.profileId())!=1) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        var fields=mapper.fields(row.profileId(),row.clientId(),addressType(row));
        if (fields==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        return fields;
    }
    public List<ClientDraftContact> contacts(long profile) { return mapper.contacts(profile); }
    public void create(ClientDraftCreation row,String snapshot) {
        mapper.insertParty(row);mapper.insertClient(row);mapper.insertApplication(row);mapper.insertProfile(row);
        if (row.type()==ClientType.INDIVIDUAL) mapper.insertIndividual(row); else mapper.insertOrganization(row);
        mapper.insertBusiness(row);mapper.insertAddress(row);mapper.insertIdentifier(row);
        mapper.insertHistoryRoot(row);mapper.insertHistory(row,snapshot);
    }
    public void save(ClientDraftIdentity row,ClientDraftPatch patch,Map<String,ClientDraftContact> contacts,Instant now) {
        Map<String,Object> changes=patch.databaseValues();
        if (has(changes,"individual")) one(mapper.saveIndividual(row.profileId(),changes,now));
        if (has(changes,"organization")) one(mapper.saveOrganization(row.profileId(),changes,now));
        if (has(changes,"business")) one(mapper.saveBusiness(row.clientId(),changes,now));
        if (has(changes,"address")) one(mapper.saveAddress(row.profileId(),addressType(row),changes,now));
        if (has(changes,"identifier")) {
            if (mapper.identifiers(row.profileId())!=1) throw new FailException(APPLICATION_NOT_EDITABLE);
            // 退回后改证件：先解绑旧身份键，重提时按新号码重新绑定（同号码复用原键）。
            mapper.unbindIdentifier(row.profileId(),now);
            one(mapper.saveIdentifier(row.profileId(),changes,now));
        }
        for (var entry:contacts.entrySet()) {
            if (entry.getValue()==null) mapper.clearContact(row.profileId(),entry.getKey());
            else mapper.saveContact(ids.nextId(),row.profileId(),entry.getValue(),now);
        }
        one(mapper.touchProfile(row.profileId(),now));one(mapper.touchApplication(row.applicationId(),now));
    }
    private static String addressType(ClientDraftIdentity row) { return row.clientType()==ClientType.INDIVIDUAL?"RESIDENTIAL":"REGISTERED"; }
    private static boolean has(Map<String,Object> values,String group) { return values.keySet().stream().anyMatch(k -> k.startsWith(group+"_")); }
    private static void one(int count) { if (count!=1) throw new ErrorException(DRAFT_CONTEXT_INVALID); }
}
