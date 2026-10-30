package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.DeactivateRiskListEntryContext;
import com.paytech.risk.control.domain.list.RiskListEntryView;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.RiskListService;
import com.paytech.risk.control.service.processor.request.DeactivateRiskListEntryRequest;

import lombok.AllArgsConstructor;

/**
 * 人工失效名单成员
 * <li>只追加状态并结束生效区间，不删除记录；历史时刻的命中判断仍按原区间成立。</li>
 * <li>限额档位名单（TIER）由系统维护，拒绝人工失效。</li>
 */
@Component
@AllArgsConstructor
public class DeactivateRiskListEntryProcessor
    extends AbstractLockProcessor<DeactivateRiskListEntryRequest, RiskListEntryView> {
    private final Clock clock;
    private final RiskListService service;
    private final RiskListRepository lists;
    private final RiskListEntryRepository entries;

    /** 立即尝试获取名单维护锁，与同名单其他成员维护互斥。 */
    @Override
    protected LockCommand lockCommand(DeactivateRiskListEntryRequest request) {
        return LockCommand.tryLocks(List.of(RiskListLocks.list(request.getListCode())), RiskListLocks.WAIT_TIME,
            RiskListLocks.LEASE_TIME);
    }

    @Override
    protected RiskListEntryView processWithinLock(DeactivateRiskListEntryRequest request) {
        // 业务验证
        RiskList list = lists.findByCode(request.getListCode())
            .orElseThrow(() -> new BizCheckFailException("名单不存在: " + request.getListCode()));
        RiskListLocks.requireManualMaintenance(list);
        RiskListEntry entry = entries.findById(request.getEntryId())
            .filter(value -> value.getListId() == list.getListId())
            .orElseThrow(() -> new BizCheckFailException("名单成员不存在: " + request.getEntryId()));
        if (entry.getStatus() != RiskListEntryStatus.ACTIVE) {
            throw new BizCheckFailException("名单成员已失效: " + request.getEntryId());
        }

        // 保存
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        service.deactivateEntry(new DeactivateRiskListEntryContext(entry, request.getActorAccountId(),
            request.getReason(), now));
        return RiskListEntryView.of(entry);
    }
}
