package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.RiskList;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domain.list.AddRiskListEntryContext;
import com.paytech.risk.control.domain.list.RiskListEntryView;
import com.paytech.risk.control.domain.list.RiskSubjectKeys;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.RiskListService;
import com.paytech.risk.control.service.processor.request.AddRiskListEntryRequest;

import lombok.AllArgsConstructor;

/**
 * 人工添加名单成员
 * <li>名单资源锁串行同一名单的成员维护；同名单同主体至多一个生效成员，由生效槽位唯一索引兜底。</li>
 * <li>旧成员已到期但仍占用槽位时，在同一事务中先标记过期再插入新成员。</li>
 */
@Component
@AllArgsConstructor
public class AddRiskListEntryProcessor extends AbstractLockProcessor<AddRiskListEntryRequest, RiskListEntryView> {
    private final Clock clock;
    private final RiskListService service;
    private final RiskListRepository lists;
    private final RiskListEntryRepository entries;
    private final ControlIdentifierGenerator identifiers;

    /** 立即尝试获取名单维护锁，与同名单其他成员维护互斥。 */
    @Override
    protected LockCommand lockCommand(AddRiskListEntryRequest request) {
        return LockCommand.tryLocks(List.of(RiskListLocks.list(request.getListCode())), RiskListLocks.WAIT_TIME,
            RiskListLocks.LEASE_TIME);
    }

    @Override
    protected RiskListEntryView processWithinLock(AddRiskListEntryRequest request) {
        // 业务验证
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        RiskList list = RiskListLocks.requireActiveList(lists, request.getListCode());
        String subjectKey = RiskSubjectKeys.normalize(list.getSupportedSubjectType(), request.getSubjectValue());
        Instant effectiveFrom = request.getEffectiveFrom() == null ? now : request.getEffectiveFrom();
        if (request.getEffectiveTo() != null && !request.getEffectiveTo().isAfter(effectiveFrom)) {
            throw new BizCheckFailException("名单成员生效终点必须晚于生效起点");
        }
        RiskListEntry expired = entries.findActive(list.getListId(), list.getSupportedSubjectType(), subjectKey)
            .map(current -> requireExpired(current, now)).orElse(null);

        // 保存
        RiskListEntry entry = buildEntry(request, list, subjectKey, effectiveFrom, now);
        service.addEntry(new AddRiskListEntryContext(list, entry, expired));
        return RiskListEntryView.of(entry);
    }

    /** 仍在有效期内的生效成员不能被重复添加。 */
    RiskListEntry requireExpired(RiskListEntry current, Instant now) {
        if (current.getEffectiveTo() == null || current.getEffectiveTo().isAfter(now)) {
            throw new ConcurrentRequestException("名单中已有该主体的生效成员: " + current.getEntryId());
        }
        return current;
    }

    /** 构造人工来源的生效成员。 */
    RiskListEntry buildEntry(AddRiskListEntryRequest request, RiskList list, String subjectKey, Instant effectiveFrom,
        Instant now) {
        RiskListEntry entry = new RiskListEntry();
        entry.setEntryId(identifiers.nextIdentifier());
        entry.setListId(list.getListId());
        entry.setSubjectType(list.getSupportedSubjectType());
        entry.setSubjectKey(subjectKey);
        entry.setSourceType(RiskListEntrySource.MANUAL);
        entry.setReason(request.getReason());
        entry.setCreatedBy(request.getActorAccountId());
        entry.setStatus(RiskListEntryStatus.ACTIVE);
        entry.setActiveSlot(Boolean.TRUE);
        entry.setEffectiveFrom(effectiveFrom);
        entry.setEffectiveTo(request.getEffectiveTo());
        entry.setVersion(1);
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        return entry;
    }
}
