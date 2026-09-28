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
    /** 仅读取本账号摘要来源，不创建工作区或执行恢复。 */
    public List<ClientDraftIdentity> recentApplications(long accountId) { return mapper.recentApplications(accountId); }
    public Long activeApplication(long account) { return mapper.activeApplication(account); }
    public ClientDraftCommandRecord command(ClientDraftCommand command) { return mapper.command(command); }
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
    public void receipt(long id,ClientDraftCommand command,long application,String operation,Instant now) {
        // 本切片不清理命令记录；已存在的原命令即使超出保留窗口也不能重新创建申请。
        mapper.insertReceipt(id,command,operation+":"+application,now,now.plus(Duration.ofDays(90)));
    }
    public void save(ClientDraftIdentity row,ClientDraftPatch patch,Map<String,ClientDraftContact> contacts,Instant now) {
        Map<String,Object> changes=patch.databaseValues();
        if (has(changes,"individual")) one(mapper.saveIndividual(row.profileId(),changes,now));
        if (has(changes,"organization")) one(mapper.saveOrganization(row.profileId(),changes,now));
        if (has(changes,"business")) one(mapper.saveBusiness(row.clientId(),changes,now));
        if (has(changes,"address")) one(mapper.saveAddress(row.profileId(),addressType(row),changes,now));
        if (has(changes,"identifier")) {
            if (mapper.identifiers(row.profileId())!=1 || mapper.boundIdentifiers(row.profileId())!=0)
                throw new FailException(APPLICATION_NOT_EDITABLE);
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
