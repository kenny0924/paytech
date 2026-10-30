package com.paytech.risk.control.service.processor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domain.limittier.TierClientProfile;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.processor.request.DecideClientTierChangeRequest;

/**
 * 档位写入口共用的资源锁、前置检查与建档构造
 * <li>同一 Client 的申请、撤回、审批与驳回共用一把锁 risk-limit-tier:client:{clientId}，不与承保锁嵌套；承保批准建档不取档位锁，
 * 由档位行 client_id 唯一索引兜底。</li>
 * <li>锁只减少竞争，最终由版本 CAS、待审批槽位与名单生效槽位唯一索引保护。</li>
 */
final class LimitTierCommands {
    /** 锁等待时长。 */
    static final Duration WAIT_TIME = Duration.ofMillis(500);

    /** 锁租约。 */
    static final Duration LEASE_TIME = Duration.ofSeconds(30);

    /** 承保批准建档的变更理由。 */
    static final String UNDERWRITING_REASON = "承保批准";

    /** 纯工具。 */
    private LimitTierCommands() { }

    /** Client 档位锁 Key。 */
    static String client(long clientId) {
        return "risk-limit-tier:client:" + clientId;
    }

    /** Client 必须已有档位。 */
    static ClientLimitTier requireTier(ClientLimitTierRepository tiers, long clientId) {
        return tiers.findByClientId(clientId)
            .orElseThrow(() -> new LimitTierException(Reason.NOT_FOUND, "Client " + clientId + " 没有限额档位"));
    }

    /** 申请必须存在。 */
    static ClientLimitTierChange requireChange(ClientLimitTierChangeRepository changes, long changeId) {
        return changes.findById(changeId)
            .orElseThrow(() -> new LimitTierException(Reason.CHANGE_NOT_FOUND, "档位变更申请不存在: " + changeId));
    }

    /** 申请须仍待审批且版本为调用方所见。 */
    static void requirePending(ClientLimitTierChange change, int expectedVersion) {
        if (change.getStatus() != LimitTierChangeStatus.PENDING) {
            throw new LimitTierException(Reason.CHANGE_STATE_INVALID,
                "档位变更申请 " + change.getChangeId() + " 当前为 " + change.getStatus().getCode() + "，不是待审批");
        }
        if (change.getVersion() != expectedVersion) {
            throw new LimitTierException(Reason.VERSION_CONFLICT, "档位变更申请 " + change.getChangeId() + " 已被修改，当前版本 "
                + change.getVersion() + "，期望 " + expectedVersion);
        }
    }

    /** 审批级别与四眼：普通接口不能处理 ELEVATED 申请，审批人不能是申请人。 */
    static void requireDecider(ClientLimitTierChange change, DecideClientTierChangeRequest request) {
        if (change.getApprovalLevel() == LimitTierApprovalLevel.ELEVATED && !request.isElevated()) {
            throw new LimitTierException(Reason.ELEVATION_REQUIRED, "档位变更申请 " + change.getChangeId()
                + " 的审批级别为 ELEVATED（CRR " + (change.getCrrSnapshot() == null ? "未评" : change.getCrrSnapshot().getCode())
                + "），须由高级审批人处理");
        }
        if (Objects.equals(change.getRequestedBy(), request.getActorAccountId())) {
            throw new LimitTierException(Reason.SELF_APPROVAL, "不能审批自己提交的档位变更申请: " + change.getChangeId());
        }
    }

    /** 系统维护的 TIER 名单必须存在、启用且用途为 TIER；缺失属于初始化数据被破坏。 */
    static RiskList requireTierList(RiskListRepository lists, LimitTier tier) {
        RiskList list = lists.findByCode(tier.listCode())
            .orElseThrow(() -> new ErrorException("限额档位名单缺失: " + tier.listCode()));
        if (list.getListPurpose() != RiskListPurpose.TIER || list.getStatus() != RiskListStatus.ACTIVE
            || list.getSupportedSubjectType() != RiskSubjectType.CLIENT) {
            throw new ErrorException("限额档位名单不是启用的 CLIENT TIER 名单: " + tier.listCode());
        }
        return list;
    }

    /**
     * 构造 TIER 名单的新生效成员
     *
     * @param entryId 成员 ID
     * @param list TIER 名单
     * @param clientId Client ID
     * @param changeId 产生该成员的变更记录 ID，写入原因
     * @param supersedesEntryId 被替代的旧档位成员 ID；可空
     * @param now 生效起点
     * @return 长期有效的成员
     */
    static RiskListEntry newEntry(long entryId, RiskList list, long clientId, long changeId, Long supersedesEntryId,
        Instant now) {
        RiskListEntry entry = new RiskListEntry();
        entry.setEntryId(entryId);
        entry.setListId(list.getListId());
        entry.setSubjectType(RiskSubjectType.CLIENT);
        entry.setSubjectKey(Long.toString(clientId));
        entry.setSourceType(RiskListEntrySource.TIER);
        entry.setReason("change:" + changeId);
        entry.setStatus(RiskListEntryStatus.ACTIVE);
        entry.setActiveSlot(Boolean.TRUE);
        entry.setEffectiveFrom(now);
        entry.setSupersedesEntryId(supersedesEntryId);
        entry.setVersion(1);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        return entry;
    }

    /**
     * 构造建档的三行：档位行、直接 APPROVED 的建档记录、TIER 名单成员
     *
     * @param ids 标识生成
     * @param lists 名单仓储，读取目标档位名单
     * @param client 冻结的 Client 展示字段
     * @param target 建档档位与来源（承保初始化为 VERIFICATION，迁移为 STANDARD）
     * @param underwritingId 有效承保版本 ID；可空
     * @param crr 有效承保 CRR；可空
     * @param actor 承保决定人、触发人或迁移执行人；可空
     * @param now 建档时间
     * @return 建档上下文
     */
    static LimitTierEstablishContext establishment(ControlIdentifierGenerator ids, RiskListRepository lists,
        TierClientProfile client, Establishment target, Long underwritingId, UnderwritingRiskLevel crr, Long actor,
        Instant now) {
        ClientLimitTierChange change = establishmentChange(ids.nextIdentifier(), client.clientId(), target, now);
        change.setUnderwritingId(underwritingId);
        change.setCrrSnapshot(crr);
        change.setRequestedBy(target.source() == LimitTierSource.MIGRATION ? actor : null);
        ClientLimitTier tier = new ClientLimitTier();
        tier.setClientLimitTierId(ids.nextIdentifier());
        tier.setClientId(client.clientId());
        tier.setTier(target.tier());
        tier.setClientDisplayName(client.clientDisplayName());
        tier.setClientNo(client.clientNo());
        tier.setClientType(client.clientType());
        tier.setJurisdictionCode(client.jurisdictionCode());
        tier.setSourceType(target.source());
        tier.setSourceChangeId(change.getChangeId());
        tier.setEffectiveTime(now);
        tier.setCreatedBy(actor);
        tier.setVersion(1);
        tier.setCreateTime(now);
        tier.setUpdateTime(now);
        RiskListEntry entry = newEntry(ids.nextIdentifier(), requireTierList(lists, target.tier()), client.clientId(),
            change.getChangeId(), null, now);
        return new LimitTierEstablishContext(tier, change, entry);
    }

    /**
     * 承保批准时的档位初始化（B2）：首次批准或修订批准，只要该 Client 尚无档位就建 VERIFICATION；已有档位时不改
     *
     * @param tiers 档位仓储
     * @param lists 名单仓储
     * @param ids 标识生成
     * @param client 承保快照中的 Client 展示字段
     * @param underwritingId 本次批准的承保版本 ID
     * @param crr 承保快照 CRR；可空
     * @param actor 承保决定人或自动承保触发人
     * @param now 批准时间
     * @return 建档上下文；不需要初始化时为空
     */
    static LimitTierEstablishContext underwritingInitialization(ClientLimitTierRepository tiers, RiskListRepository lists,
        ControlIdentifierGenerator ids, TierClientProfile client, long underwritingId,
        UnderwritingRiskLevel crr, long actor, Instant now) {
        if (tiers.findByClientId(client.clientId()).isPresent()) {
            return null;
        }
        return establishment(ids, lists, client, Establishment.underwriting(), underwritingId, crr, actor, now);
    }

    /** 承保版本行冻结的 Client 展示字段。 */
    static TierClientProfile profile(ClientUnderwriting row) {
        return new TierClientProfile(row.getClientId(), row.getClientDisplayName(), row.getClientNo(), row.getClientType(),
            row.getJurisdictionCode());
    }

    /** 建档记录：无前档位、直接 APPROVED、无审批级别。 */
    static ClientLimitTierChange establishmentChange(long changeId, long clientId, Establishment target, Instant now) {
        ClientLimitTierChange change = new ClientLimitTierChange();
        change.setChangeId(changeId);
        change.setClientId(clientId);
        change.setChangeType(target.source());
        change.setToTier(target.tier());
        change.setStatus(LimitTierChangeStatus.APPROVED);
        change.setReason(target.reason());
        change.setRequestedTime(now);
        change.setVersion(1);
        change.setCreateTime(now);
        change.setUpdateTime(now);
        return change;
    }

    /**
     * 建档的档位、来源与理由
     *
     * @param tier 建档档位
     * @param source 来源：UNDERWRITING_INIT 或 MIGRATION
     * @param reason 变更理由
     */
    record Establishment(LimitTier tier, LimitTierSource source, String reason) {
        /** 承保批准建档：VERIFICATION。 */
        static Establishment underwriting() {
            return new Establishment(LimitTier.VERIFICATION, LimitTierSource.UNDERWRITING_INIT, UNDERWRITING_REASON);
        }

        /** 存量迁移：STANDARD。 */
        static Establishment migration(String reason) {
            return new Establishment(LimitTier.STANDARD, LimitTierSource.MIGRATION, reason);
        }
    }
}
