package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.LimitTierApprovalContext;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domain.limittier.LimitTierPlacement;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.request.DecideClientTierChangeRequest;

import lombok.AllArgsConstructor;

/**
 * 批准档位变更申请（L6 普通、L7 高级，四眼）
 * <li>普通接口只能处理 STANDARD 级别（CRR=LOW），ELEVATED 只能经高级接口；审批人不能是申请人。</li>
 * <li>批准即生效：申请置为 APPROVED、档位行改为目标档位（进入或延长观察期重置开始时间，进入 RESTRICTED 记住原档位）、
 * 旧 TIER 名单成员置为 SUPERSEDED、新 TIER 名单成员插入，同一事务完成；名单命中无缓存，下一次评估即生效。</li>
 */
@Component
@AllArgsConstructor
public class ApproveClientTierChangeProcessor
    extends AbstractLockProcessor<DecideClientTierChangeRequest, ClientTierChangeView> {
    private final Clock clock;
    private final RiskListRepository lists;
    private final ClientLimitTierService service;
    private final RiskListEntryRepository entries;
    private final ClientLimitTierRepository tiers;
    private final ControlIdentifierGenerator identifiers;
    private final ClientLimitTierChangeRepository changes;

    /** 按申请所属 Client 获取档位锁；申请不存在直接返回 CHANGE_NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(DecideClientTierChangeRequest request) {
        long clientId = LimitTierCommands.requireChange(changes, request.getChangeId()).getClientId();
        return LockCommand.tryLocks(List.of(LimitTierCommands.client(clientId)), LimitTierCommands.WAIT_TIME,
            LimitTierCommands.LEASE_TIME);
    }

    @Override
    protected ClientTierChangeView processWithinLock(DecideClientTierChangeRequest request) {
        // 业务验证
        ClientLimitTierChange change = LimitTierCommands.requireChange(changes, request.getChangeId());
        LimitTierCommands.requirePending(change, request.getExpectedVersion());
        LimitTierCommands.requireDecider(change, request);
        ClientLimitTier tier = LimitTierCommands.requireTier(tiers, change.getClientId());
        requireUnchangedTier(tier, change);

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        service.approve(buildContext(change, tier, request, now));
        return ClientTierChangeView.of(change);
    }

    /** 申请后档位不应变化；变化说明数据被并发改动。 */
    static void requireUnchangedTier(ClientLimitTier tier, ClientLimitTierChange change) {
        if (tier.getTier() != change.getFromTier()) {
            throw new LimitTierException(Reason.VERSION_CONFLICT, "Client " + tier.getClientId() + " 的档位已从 "
                + change.getFromTier().getCode() + " 变为 " + tier.getTier().getCode() + "，请刷新后重试");
        }
    }

    /** 构造批准上下文；档位不变（延长观察）时不替换名单成员。 */
    LimitTierApprovalContext buildContext(ClientLimitTierChange change, ClientLimitTier tier,
        DecideClientTierChangeRequest request, Instant now) {
        LimitTierPlacement placement = placement(change, now);
        RiskListEntry closing = null;
        RiskListEntry created = null;
        if (change.getFromTier() != change.getToTier()) {
            closing = activeEntry(change.getFromTier(), change.getClientId());
            RiskList target = LimitTierCommands.requireTierList(lists, change.getToTier());
            created = LimitTierCommands.newEntry(identifiers.nextIdentifier(), target, change.getClientId(),
                change.getChangeId(), closing == null ? null : closing.getEntryId(), now);
        }
        return new LimitTierApprovalContext(change, tier, placement, closing, created, request.getActorAccountId(),
            blankToNull(request.getComment()), now);
    }

    /** 档位行新值：进入或延长 OBSERVATION 写观察期，进入 RESTRICTED 记住原档位。 */
    static LimitTierPlacement placement(ClientLimitTierChange change, Instant now) {
        boolean observation = change.getToTier() == LimitTier.OBSERVATION;
        return new LimitTierPlacement(change.getToTier(),
            change.getToTier() == LimitTier.RESTRICTED ? change.getFromTier() : null, LimitTierSource.REQUEST,
            change.getChangeId(), now, observation ? now : null,
            observation ? change.getObservationTargetDays() : null,
            observation ? change.getObservationTargetTransactions() : null,
            observation ? change.getChangeId() : null);
    }

    /** 旧档位名单中该 Client 的生效成员；投影缺失时为空，批准同时修复。 */
    RiskListEntry activeEntry(LimitTier tier, long clientId) {
        RiskList list = LimitTierCommands.requireTierList(lists, tier);
        return entries.findActive(list.getListId(), RiskSubjectType.CLIENT, Long.toString(clientId)).orElse(null);
    }

    /** 空白意见按未填写处理。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
