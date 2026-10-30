package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 风险事件主体关联的单表持久化与窗口查询边界。 */
public interface VelocityEventSubjectRepository {
    /** 批量插入；空集合不访问数据库。 */
    void insertAll(List<VelocityEventSubject> rows);

    /** 统计锚定主体在窗口 [from, to) 内的事件数。 */
    long countEvents(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to);

    /** 合计锚定主体在窗口内与指定币种相同的事件金额，没有事件时为零。 */
    BigDecimal sumAmount(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to, String currency);


    /** 统计锚定主体在窗口内关联过的不同去重主体数。 */
    long countDistinct(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to, RiskSubjectType distinctType);

    /** 读取锚定主体在窗口内最近一次事件。 */
    Optional<VelocityEvent> findLast(RiskSubjectType subjectType, String subjectKey, VelocityEventCode eventCode, Instant from, Instant to);
}
