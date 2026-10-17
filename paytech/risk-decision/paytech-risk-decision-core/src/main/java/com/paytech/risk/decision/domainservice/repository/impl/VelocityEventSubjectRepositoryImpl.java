package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;
import com.paytech.risk.decision.core.dal.mapper.VelocityEventSubjectMapper;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domainservice.repository.VelocityEventSubjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 风险事件主体持久化与窗口查询。 */
@Repository
@AllArgsConstructor
public class VelocityEventSubjectRepositoryImpl implements VelocityEventSubjectRepository {
    private final VelocityEventSubjectMapper mapper;

    /** 批量插入；空集合不访问数据库。 */
    @Override
    public void insertAll(List<VelocityEventSubject> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }

    /** 统计锚定主体在窗口 [from, to) 内的事件数。 */
    @Override
    public long countEvents(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to) {
        return mapper.countEvents(subjectType, subjectKey, eventCode, from, to);
    }

    /** 合计锚定主体在窗口内与指定币种相同的事件金额，没有事件时为零。 */
    @Override
    public BigDecimal sumAmount(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to, String currency) {
        return mapper.sumAmount(subjectType, subjectKey, eventCode, from, to, currency);
    }

    /** 统计锚定主体在窗口内关联过的不同去重主体数。 */
    @Override
    public long countDistinct(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to, RiskSubjectType distinctType) {
        return mapper.countDistinct(subjectType, subjectKey, eventCode, from, to, distinctType);
    }

    /** 读取锚定主体在窗口内最近一次事件。 */
    @Override
    public Optional<VelocityEvent> findLast(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to) {
        return Optional.ofNullable(mapper.selectLast(subjectType, subjectKey, eventCode, from, to));
    }
}
