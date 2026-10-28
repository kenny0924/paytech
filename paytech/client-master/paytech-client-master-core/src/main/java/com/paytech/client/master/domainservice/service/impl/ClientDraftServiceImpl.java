package com.paytech.client.master.domainservice.service.impl;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.master.core.dal.dataobject.*;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;
import com.paytech.client.master.domainservice.ClientProjectionSnapshot;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.foundation.common.error.*;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
/** CCT 业务规则和原子写集合；事务由 Processor 包裹，内部只访问本库。 */
public final class ClientDraftServiceImpl implements ClientDraftService {
    private final ClientDraftRepository repository;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final IdGenerator ids;
    private final Clock clock;
    private final CompensationEventRegistrar events;
    private final PartyRoleRepository partyRoles;
    private final PartyAttributeRepository partyAttributes;
    private final PartnerOnboardingCodeRepository onboardingCodes;
    private final ApplicationDeclarationRepository declarations;
    private final LocalPartnerRepository localPartners;
    private final RuleSettingRepository ruleSettings;
    public ClientDraftServiceImpl(ClientDraftRepository repository,ClientPeopleRepository people,ClientDocumentRepository documents,IdGenerator ids,Clock clock,CompensationEventRegistrar events,
        PartyRoleRepository partyRoles,PartyAttributeRepository partyAttributes,PartnerOnboardingCodeRepository onboardingCodes,
        ApplicationDeclarationRepository declarations,LocalPartnerRepository localPartners,RuleSettingRepository ruleSettings) {
        this.repository=repository;this.people=people;this.documents=documents;this.ids=ids;this.clock=clock;this.events=events;
        this.partyRoles=partyRoles;this.partyAttributes=partyAttributes;this.onboardingCodes=onboardingCodes;
        this.declarations=declarations;this.localPartners=localPartners;this.ruleSettings=ruleSettings;
    }
    public CommandReceiptResponseBody create(ClientDraftCommand command,ClientType type,ClientApplicationTerms terms,PartnerOnboardingCode code) {
        requireTransaction();
        // 已有未完成申请时恢复原申请：同一请求重复到达也落在这里，不占用入驻码。
        Long existing=repository.activeApplication(command.accountId());
        if (existing!=null) {
            var row=identity(existing,command.accountId(),true);
            if (row.activeSlot()==null) throw new ErrorException(DRAFT_CONTEXT_INVALID);
            if (row.clientType()!=type) throw new FailException(ACTIVE_APPLICATION_TYPE_MISMATCH);
            return receipt(row,"RESTORED",now());
        }
        Instant now=now();
        // 入驻码在新建或重新申请的同一事务占用一次；占用失败（并发用尽、作废或过期）整体回滚。
        if (code!=null && !onboardingCodes.consume(code,now)) throw new FailException(OnboardingErrorCode.ONBOARDING_CODE_INVALID);
        var rejected=repository.rejectedApplication(command.accountId(),type);
        if (rejected!=null) {
            return reapply(command,rejected,terms);
        }
        var creation=new ClientDraftCreation(ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),
            ids.nextId(),ids.nextId(),ids.nextId(),ids.nextId(),command.accountId(),type,terms,now);
        var history=new ClientProjectionHistory(creation.changeId(),creation.historyId(),Long.toString(creation.clientId()),1,
            BusinessChangeOperation.CREATE,BusinessChangeSnapshotFormat.ENTITY_NO,null,command.accountId(),now,
            creation.clientId(),creation.partyId(),creation.clientNo(),command.accountId(),1);
        String snapshot=ClientProjectionSnapshot.snapshot(history,ClientStatus.PENDING,
            Long.toString(creation.commandRecordId()),"CLIENT_CREATED");
        repository.create(creation,snapshot);
        partyRoles.insert(clientRole(creation));
        events.register(ClientTenantProjectionHandler.buildEvent(creation.changeId()));
        return receipt(identity(creation.applicationId(),command.accountId(),false),"CREATED",now);
    }
    /** 原账号条件由同库查询执行，返回前验证每个身份绑定完整。 */
    public List<ClientDraftIdentity> recentApplications(long accountId) {
        return repository.recentApplications(accountId).stream().map(ClientDraftPolicy::requireValid).toList();
    }
    public ClientDraftView read(long accountId,long applicationId) {
        requireTransaction();
        return view(identity(applicationId,accountId,false),applicationId);
    }
    public ClientDraftView readForReview(long applicationId) {
        requireTransaction();
        return view(ClientDraftPolicy.requireValid(repository.reviewIdentity(applicationId)),applicationId);
    }
    private ClientDraftView view(ClientDraftIdentity row,long applicationId) {
        var persons=row.clientType()==ClientType.ORGANIZATION?people.people(row):List.<ClientPersonView>of();
        var relations=row.clientType()==ClientType.ORGANIZATION?people.relations(row):List.<ClientPartyRelation>of();
        var visibleIds=persons.stream().map(person -> person.identity().partyId()).collect(java.util.stream.Collectors.toSet());
        if (relations.stream().anyMatch(relation -> !visibleIds.contains(relation.relatedPartyId()))) throw new ErrorException(DRAFT_CONTEXT_INVALID);
        var profileIds=new java.util.ArrayList<Long>();
        profileIds.add(row.profileId());
        persons.forEach(person -> profileIds.add(person.identity().profileId()));
        var latest=latestDeclarations(applicationId);
        return new ClientDraftView(row,repository.fields(row),repository.contacts(row.profileId()),persons,relations,
            documents.documents(applicationId),partyAttributes.findByProfiles(profileIds),latest,
            responsiblePartnerName(row.responsiblePartnerId()),documentVersions(latest));
    }
    /** 已有声明类型配置的文档版本（DECLARATION 作用域的 DOCUMENT_VERSION，含 * 默认）；未配置的类型不出现。 */
    private Map<String,String> documentVersions(List<ApplicationDeclaration> latest) {
        if (latest.isEmpty()) {
            return Map.of();
        }
        var scopes=new ArrayList<String>();
        latest.forEach(row -> scopes.add(row.getDeclarationType()));
        scopes.add(OnboardingPack.ANY);
        var rules=new RuleSettings(ruleSettings.findActive(scopes));
        var versions=new HashMap<String,String>();
        latest.forEach(row -> {
            String version=rules.value(RuleCode.DOCUMENT_VERSION,row.getDeclarationType());
            if (version!=null) {
                versions.put(row.getDeclarationType(),version);
            }
        });
        return versions;
    }
    /** 每种声明的最新一条（按声明时间追加），保持首次出现顺序。 */
    private List<ApplicationDeclaration> latestDeclarations(long applicationId) {
        var latest=new java.util.LinkedHashMap<String,ApplicationDeclaration>();
        declarations.findByApplication(applicationId).forEach(row -> latest.put(row.getDeclarationType(),row));
        return List.copyOf(latest.values());
    }
    /** 责任 PayFac 的法定名称；平台自营为空。 */
    private String responsiblePartnerName(Long responsiblePartnerId) {
        if (responsiblePartnerId==null) {
            return null;
        }
        var partner=localPartners.findAdminView(responsiblePartnerId);
        return partner==null?null:partner.getLegalName();
    }
    public ClientDraftSource prepareSave(ClientDraftCommand command,long applicationId,ClientDraftPatch patch) {
        requireTransaction();
        var row=identity(applicationId,command.accountId(),false);
        editable(row);patch.requireType(row.clientType());
        return new ClientDraftSource(row,repository.fields(row));
    }
    /**
     * 保存事务外已准备的写入
     * <li>重新加锁读取申请并复核可编辑；资料列、扩展属性、联系方式与 Schema 切换同一事务提交。</li>
     * <li>有实际写入时资料版本加一，此前的声明随之失效。</li>
     */
    public CommandReceiptResponseBody save(ClientDraftCommand command,long applicationId,ClientDraftChanges changes,
        Map<String,ClientDraftContact> contacts) {
        requireTransaction();
        var row=identity(applicationId,command.accountId(),true);
        editable(row);
        Instant now=now();
        repository.save(row,changes,contacts,now);
        saveAttributes(row.profileId(),changes,now);
        if (!changes.isEmpty() || !contacts.isEmpty()) {
            repository.bumpDraftRevision(applicationId);
        }
        return receipt(row,"SAVED",now);
    }
    /** 扩展属性逐项写入或清空。 */
    private void saveAttributes(long profileId,ClientDraftChanges changes,Instant now) {
        changes.getAttributes().forEach((code,value) -> {
            if (value==null) {
                partyAttributes.delete(profileId,code);
                return;
            }
            var row=new PartyAttribute();
            row.setAttributeId(ids.nextId());row.setProfileId(profileId);row.setAttributeCode(code);
            row.setValueText(value.text());row.setValueDate(value.date());row.setValueDecimal(value.decimal());
            row.setCreateTime(now);row.setUpdateTime(now);
            partyAttributes.save(row);
        });
    }
    /** 同类型被拒或撤回后重新申请：沿用原 Client、Party 与工作区，不新建 Client，也不再投影 Platform。 */
    private CommandReceiptResponseBody reapply(ClientDraftCommand command,ClientDraftIdentity rejected,ClientApplicationTerms terms) {
        Instant now=now();
        long applicationId=ids.nextId();
        repository.reapply(rejected,applicationId,terms,now);
        return receipt(identity(applicationId,command.accountId(),false),"CREATED",now);
    }
    private ClientDraftIdentity identity(long applicationId,long accountId,boolean lock) {
        return ClientDraftPolicy.requireValid(repository.identity(applicationId,accountId,lock));
    }
    private void editable(ClientDraftIdentity row) { if (!ClientDraftPolicy.isEditable(row)) throw new FailException(APPLICATION_NOT_EDITABLE); }
    /** commandId 已停用，恒为空；请求幂等由 Gateway 处理。 */
    private CommandReceiptResponseBody receipt(ClientDraftIdentity row,String operation,Instant time) {
        return new CommandReceiptResponseBody(null,Long.toString(row.applicationId()),row.applicationNo(),Long.toString(row.clientId()),
            row.clientType().getCode(),operation,new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time));
    }
    /** 新建 Client 在统一角色索引中的 CLIENT 行，与 Client 同一事务写入。 */
    private PartyRole clientRole(ClientDraftCreation creation) {
        PartyRole role=new PartyRole();
        role.setPartyRoleId(ids.nextId());
        role.setPartyId(creation.partyId());
        role.setRoleCode(PartyRoleCode.CLIENT);
        role.setSubjectId(Long.toString(creation.clientId()));
        role.setStatus(PartyRoleStatus.ACTIVE);
        role.setActiveSlot(1);
        role.setEffectiveFrom(creation.now());
        role.setCreateTime(creation.now());
        role.setUpdateTime(creation.now());
        return role;
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("草稿业务必须由入口事务包裹");
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
