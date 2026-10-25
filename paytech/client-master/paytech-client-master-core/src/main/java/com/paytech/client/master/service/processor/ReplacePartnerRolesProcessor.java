package com.paytech.client.master.service.processor;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.core.dal.dataobject.PartnerRole;
import com.paytech.client.master.core.dal.dataobject.PartyRole;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminDetail;
import com.paytech.client.master.domain.entityadmin.LocalPartnerRoleChangeContext;
import com.paytech.client.master.domain.entityadmin.RootRevision;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domain.enums.PartnerRoleStatus;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domain.enums.PartyRoleStatus;
import com.paytech.client.master.domainservice.EntityChangeSnapshot;
import com.paytech.client.master.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.client.master.domainservice.repository.EntitySourceRelationRepository;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.client.master.domainservice.service.EntityAdminQueryService;
import com.paytech.client.master.domainservice.service.EntityAdminService;
import com.paytech.client.master.service.processor.request.ReplacePartnerRolesRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * 运营整体替换 Local Partner 角色（client-master Admin API 6）
 * <li>TRY_LOCK client-master:local-partner:{localPartnerId}，等待与租约沿用 resource-lock 配置。</li>
 * <li>角色只允许 OE_PROVIDER、REFERRER，非空、不重复；仍有 ACTIVE 的 PARTNER 来源关系时不能去掉 OE_PROVIDER（422）。</li>
 * <li>锁内一个事务：缺根补建 V1、LOCAL_PARTNER 根 CAS、UPDATE 快照、结束 / 新增 t_partner_role 并同步 t_party_role。</li>
 * <li>根版本推进使 commercial 的候选复核感知提供方角色变化。affiliation 本轮不做。</li>
 */
public class ReplacePartnerRolesProcessor extends AbstractLockProcessor<ReplacePartnerRolesRequest, LocalPartnerAdminDetail> {
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final EntityAdminService entityAdminService;
    private final PartnerRoleRepository partnerRoleRepository;
    private final LocalPartnerRepository localPartnerRepository;
    private final EntityAdminQueryService entityAdminQueryService;
    private final BusinessChangeRecordRepository businessChangeRecordRepository;
    private final EntitySourceRelationRepository entitySourceRelationRepository;

    /** 锁等待与租约沿用 client-master 资源锁配置。 */
    public ReplacePartnerRolesProcessor(Clock clock, IdGenerator ids, Duration lockWait, Duration lockLease,
        EntityAdminService entityAdminService, PartnerRoleRepository partnerRoleRepository,
        LocalPartnerRepository localPartnerRepository, EntityAdminQueryService entityAdminQueryService,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        EntitySourceRelationRepository entitySourceRelationRepository) {
        this.clock = clock;
        this.ids = ids;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.entityAdminService = entityAdminService;
        this.partnerRoleRepository = partnerRoleRepository;
        this.localPartnerRepository = localPartnerRepository;
        this.entityAdminQueryService = entityAdminQueryService;
        this.businessChangeRecordRepository = businessChangeRecordRepository;
        this.entitySourceRelationRepository = entitySourceRelationRepository;
    }

    /** 角色非空、不重复且每项都可识别。 */
    @Override
    protected void validate(ReplacePartnerRolesRequest request) {
        requestedRoles(request.getRoles());
    }

    /** 同一 Partner 的主档维护共用一把锁。 */
    @Override
    protected LockCommand lockCommand(ReplacePartnerRolesRequest request) {
        return LockCommand.tryLocks(List.of("client-master:local-partner:" + request.getLocalPartnerId()), lockWait, lockLease);
    }

    @Override
    protected LocalPartnerAdminDetail processWithinLock(ReplacePartnerRolesRequest request) {
        // 查询 Partner、根与当前角色
        LocalPartner partner = localPartnerRepository.findById(request.getLocalPartnerId());
        if (partner == null) {
            throw new FailException(LOCAL_PARTNER_NOT_FOUND);
        }
        BusinessChangeRecord root = businessChangeRecordRepository.findRoot(BusinessChangeRecordRecordType.LOCAL_PARTNER,
            Long.toString(partner.localPartnerId()));
        List<PartnerRole> active = partnerRoleRepository.findActiveByPartners(List.of(partner.localPartnerId()));

        // 业务验证：仍在提供经营主体时保留 OE_PROVIDER
        Set<PartnerRoleCode> requested = requestedRoles(request.getRoles());
        if (!requested.contains(PartnerRoleCode.OE_PROVIDER)
            && entitySourceRelationRepository.isProvidingActive(partner.localPartnerId())) {
            throw new FailException(ENTITY_ATTRIBUTE_INVALID,
                "Local Partner 仍在提供经营主体，不能去掉 OE_PROVIDER: " + partner.localPartnerId());
        }

        // 构建上下文
        LocalPartnerRoleChangeContext context = buildContext(partner, root, active, requested, request);

        // 保存上下文
        entityAdminService.replacePartnerRoles(context);

        // 响应处理
        return entityAdminQueryService.localPartner(partner.localPartnerId());
    }

    /** 核对调用方版本后一次构造新增 / 结束的角色行与根推进。 */
    LocalPartnerRoleChangeContext buildContext(LocalPartner partner, BusinessChangeRecord root, List<PartnerRole> active,
        Set<PartnerRoleCode> requested, ReplacePartnerRolesRequest request) {
        long current = root == null ? 1 : root.currentVersion();
        if (current != request.getExpectedChangeVersion()) {
            throw new FailException(ENTITY_CHANGE_VERSION_CONFLICT, "Local Partner 已被修改: " + partner.localPartnerId()
                + ", 当前版本 " + current + ", 请求版本 " + request.getExpectedChangeVersion());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<PartnerRole> ended = active.stream().filter(role -> !requested.contains(role.getRoleCode())).toList();
        List<PartnerRole> added = new ArrayList<>();
        List<PartyRole> addedPartyRoles = new ArrayList<>();
        for (PartnerRoleCode code : requested) {
            if (active.stream().noneMatch(role -> role.getRoleCode() == code)) {
                added.add(partnerRole(partner, code, now));
                addedPartyRoles.add(partyRole(partner, code, now));
            }
        }
        long historyId = root == null ? ids.nextId() : root.historyId();
        long changeVersionId = ids.nextId();
        String snapshot = snapshot(partner, requested, historyId, current + 1, changeVersionId, now, request);
        RootRevision revision = new RootRevision(BusinessChangeRecordRecordType.LOCAL_PARTNER,
            Long.toString(partner.localPartnerId()), historyId, root == null, current, changeVersionId, snapshot,
            request.getActorId(), now);
        return new LocalPartnerRoleChangeContext(partner, added, ended, addedPartyRoles, revision);
    }

    /** 稳定身份、状态与替换后的角色。 */
    String snapshot(LocalPartner partner, Set<PartnerRoleCode> requested, long historyId, long versionNo, long changeVersionId,
        Instant now, ReplacePartnerRolesRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("localPartnerId", Long.toString(partner.localPartnerId()));
        data.put("partyId", Long.toString(partner.partyId()));
        data.put("status", partner.status().getCode());
        data.put("roles", requested.stream().map(PartnerRoleCode::getCode).sorted().toList());
        return EntityChangeSnapshot.update(BusinessChangeRecordRecordType.LOCAL_PARTNER, Long.toString(partner.localPartnerId()),
            historyId, versionNo, request.getActorId(), now, changeVersionId, "PARTNER_ROLES_CHANGED", request.getReason(), data);
    }

    /** 新的 ACTIVE 角色行。 */
    PartnerRole partnerRole(LocalPartner partner, PartnerRoleCode code, Instant now) {
        PartnerRole role = new PartnerRole();
        role.setPartnerRoleId(ids.nextId());
        role.setLocalPartnerId(partner.localPartnerId());
        role.setRoleCode(code);
        role.setStatus(PartnerRoleStatus.ACTIVE);
        role.setActiveSlot(1);
        role.setCreateTime(now);
        role.setUpdateTime(now);
        return role;
    }

    /** 与新角色对应的 ACTIVE 统一角色索引行。 */
    PartyRole partyRole(LocalPartner partner, PartnerRoleCode code, Instant now) {
        PartyRole role = new PartyRole();
        role.setPartyRoleId(ids.nextId());
        role.setPartyId(partner.partyId());
        role.setRoleCode(PartyRoleCode.of(code));
        role.setSubjectId(Long.toString(partner.localPartnerId()));
        role.setStatus(PartyRoleStatus.ACTIVE);
        role.setActiveSlot(1);
        role.setEffectiveFrom(now);
        role.setCreateTime(now);
        role.setUpdateTime(now);
        return role;
    }

    /** 非空、不重复、每项可识别；按 Code 排序。 */
    static Set<PartnerRoleCode> requestedRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new FailException(ENTITY_ATTRIBUTE_INVALID, "Local Partner 角色不能为空");
        }
        Set<PartnerRoleCode> result = EnumSet.noneOf(PartnerRoleCode.class);
        for (String value : roles) {
            PartnerRoleCode code = PartnerRoleCode.fromCode(value);
            if (code == null || !result.add(code)) {
                throw new FailException(ENTITY_ATTRIBUTE_INVALID, "Local Partner 角色非法或重复: " + value);
            }
        }
        return result;
    }
}
