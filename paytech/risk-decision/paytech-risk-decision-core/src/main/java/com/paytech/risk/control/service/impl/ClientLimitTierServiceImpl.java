package com.paytech.risk.control.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTier;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.ClientTierDetailView;
import com.paytech.risk.control.domain.limittier.ClientTierDetailView.QuotaUsage;
import com.paytech.risk.control.domain.limittier.ClientTierPage;
import com.paytech.risk.control.domain.limittier.ClientTierView;
import com.paytech.risk.control.domain.limittier.CurrencyUsage;
import com.paytech.risk.control.domain.limittier.LimitTierApprovalContext;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.limittier.LimitTierListFilter;
import com.paytech.risk.control.domain.limittier.LimitTierStatistics;
import com.paytech.risk.control.domain.limittier.TierObservation;
import com.paytech.risk.control.domain.limittier.TierQuotaCatalog;
import com.paytech.risk.control.domain.limittier.TierQuotaCatalog.CurrencyQuota;
import com.paytech.risk.control.domainservice.port.LimitTierUsageReader;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.service.ClientLimitTierService;

import lombok.AllArgsConstructor;

/**
 * 限额档位本地保存与查询
 * <li>档位行 client_id 唯一、申请待审批槽位唯一、档位行与申请的版本 CAS、名单生效槽位唯一共同保护并发；TIER 名单成员与档位同事务维护。</li>
 */
@Service
@AllArgsConstructor
public class ClientLimitTierServiceImpl implements ClientLimitTierService {
    /** 变更历史最多返回的条数。 */
    static final int HISTORY_LIMIT = 100;

    private final LimitTierUsageReader usage;
    private final RiskListEntryRepository entries;
    private final ClientLimitTierRepository tiers;
    private final PlatformTransactionManager transactionManager;
    private final ClientUnderwritingRepository underwritings;
    private final ClientLimitTierChangeRepository changes;

    /** 档位行唯一冲突时跳过其余两行；有外层事务时加入外层事务。 */
    @Override
    public boolean establish(LimitTierEstablishContext context) {
        Boolean created = new TransactionTemplate(transactionManager).execute(status -> {
            if (!tiers.insertIfAbsent(context.getTier())) {
                return Boolean.FALSE;
            }
            changes.insert(context.getChange());
            entries.insert(context.getEntry());
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(created);
    }

    /** 单行插入，待审批槽位冲突由仓储转换。 */
    @Override
    public void requestChange(ClientLimitTierChange change) {
        changes.insert(change);
    }

    /** 单行条件更新。 */
    @Override
    public void withdraw(ClientLimitTierChange change, long actorAccountId, Instant now) {
        changes.close(change, LimitTierChangeStatus.WITHDRAWN, actorAccountId, null, now);
    }

    /** 单行条件更新。 */
    @Override
    public void reject(ClientLimitTierChange change, long actorAccountId, String comment, Instant now) {
        changes.close(change, LimitTierChangeStatus.REJECTED, actorAccountId, comment, now);
    }

    /** 先结束申请再移动档位，最后替换名单成员；任一行数不符整体回滚。 */
    @Override
    public void approve(LimitTierApprovalContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changes.close(context.getChange(), LimitTierChangeStatus.APPROVED, context.getActorAccountId(),
                context.getComment(), context.getNow());
            tiers.moveTo(context.getTier(), context.getPlacement());
            RiskListEntry closing = context.getClosingEntry();
            if (closing != null) {
                entries.close(closing, RiskListEntryStatus.SUPERSEDED, context.getActorAccountId(),
                    "change:" + context.getChange().getChangeId(), context.getNow(), context.getNow());
            }
            if (context.getNewEntry() != null) {
                entries.insert(context.getNewEntry());
            }
        });
    }

    /** 档位行与总数取自相邻读取，管理查询允许观察相邻已提交状态；CRR、待审批与来源理由按本页批量读取。 */
    @Override
    public ClientTierPage findPage(LimitTierListFilter filter, int limit, long offset) {
        List<ClientLimitTier> rows = tiers.findPage(filter, limit, offset);
        List<Long> clientIds = rows.stream().map(ClientLimitTier::getClientId).toList();
        Map<Long, UnderwritingRiskLevel> crrs = crrByClient(clientIds);
        Map<Long, ClientLimitTierChange> pending = changes.findPendingByClientIds(clientIds).stream()
            .collect(Collectors.toMap(ClientLimitTierChange::getClientId, Function.identity()));
        Map<Long, String> reasons = changes.findByIds(rows.stream().map(ClientLimitTier::getSourceChangeId).toList()).stream()
            .collect(Collectors.toMap(ClientLimitTierChange::getChangeId, ClientLimitTierChange::getReason));
        List<ClientTierView> items = rows.stream().map(row -> ClientTierView.of(row, crrs.get(row.getClientId()),
            reasons.get(row.getSourceChangeId()), observation(row, filter.getNow()),
            ClientTierChangeView.of(pending.get(row.getClientId())))).toList();
        return new ClientTierPage(items, tiers.count(filter));
    }

    /** 无档位时只返回 CRR；有档位时按当前档位解析额度，并按币种读取 UTC 当日与近 30 日放行汇总。 */
    @Override
    public ClientTierDetailView findDetail(long clientId, Instant now) {
        UnderwritingRiskLevel crr = underwritings.findEffective(clientId).map(ClientUnderwriting::getCrr).orElse(null);
        ClientLimitTier row = tiers.findByClientId(clientId).orElse(null);
        if (row == null) {
            return new ClientTierDetailView(ClientTierView.none(clientId, crr), List.of(), List.of(), null);
        }
        String reason = changes.findById(row.getSourceChangeId()).map(ClientLimitTierChange::getReason).orElse(null);
        ClientTierView view = ClientTierView.of(row, crr, reason, observation(row, now),
            ClientTierChangeView.of(changes.findPending(clientId).orElse(null)));
        TierQuotaCatalog catalog = quotas();
        return new ClientTierDetailView(view, quotaUsages(catalog.quotas(row.getTier()), clientId, now),
            catalog.unconfiguredCurrencies(row.getTier()), row.getTier().allowsUnconfiguredCurrency());
    }

    /** 读取 TIER_ 前缀常量与集合并解析。 */
    @Override
    public TierQuotaCatalog quotas() {
        return TierQuotaCatalog.parse(usage.tierQuotaVariables());
    }

    /** 申请时间倒序。 */
    @Override
    public List<ClientTierChangeView> findHistory(long clientId) {
        return changes.findHistory(clientId, HISTORY_LIMIT).stream().map(ClientTierChangeView::of).toList();
    }

    /** 当前有效承保的 CRR，按 Client 批量读取。 */
    Map<Long, UnderwritingRiskLevel> crrByClient(List<Long> clientIds) {
        Map<Long, UnderwritingRiskLevel> crrs = new HashMap<>();
        underwritings.findEffectiveByClientIds(clientIds).forEach(row -> crrs.put(row.getClientId(), row.getCrr()));
        return crrs;
    }

    /** OBSERVATION 行按开始时间计数放行支付尝试；其他档位为空。 */
    TierObservation observation(ClientLimitTier row, Instant now) {
        if (!TierObservation.inObservation(row)) {
            return null;
        }
        long allowed = usage.countAllowedPaymentAttempts(row.getClientId(), row.getObservationStartedTime(), now);
        return TierObservation.of(row, allowed, now);
    }

    /** 本档位已配置币种的额度与已用；没有放行事件的币种已用为零。 */
    List<QuotaUsage> quotaUsages(List<CurrencyQuota> quotas, long clientId, Instant now) {
        Map<String, CurrencyUsage> used = usage.allowedPaymentAmounts(clientId, now).stream()
            .collect(Collectors.toMap(CurrencyUsage::currency, Function.identity()));
        return quotas.stream().map(quota -> {
            CurrencyUsage current = used.get(quota.currency());
            return new QuotaUsage(quota.currency(), quota.single(), quota.day(), quota.month(),
                current == null ? BigDecimal.ZERO : current.usedToday(),
                current == null ? BigDecimal.ZERO : current.usedLast30Days());
        }).toList();
    }

    /** 六次计数，允许观察相邻已提交状态。 */
    @Override
    public LimitTierStatistics statistics(Instant now) {
        return new LimitTierStatistics(tiers.countByTier(LimitTier.VERIFICATION), tiers.countByTier(LimitTier.OBSERVATION),
            tiers.countByTier(LimitTier.STANDARD), tiers.countByTier(LimitTier.RESTRICTED), changes.countPending(),
            tiers.countObservationDue(now));
    }
}
