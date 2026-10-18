package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 风险事件与受控主体的关联表映射。 */
public interface VelocityEventSubjectMapper {
    /** 插入一行。 */
    int insert(@Param("row") VelocityEventSubject row);

    /** 批量插入；空集合由调用方跳过。 */
    int insertBatch(@Param("rows") List<VelocityEventSubject> rows);

    /** 统计锚定主体在窗口 [from, to) 内的事件数。 */
    long countEvents(@Param("subjectType") RiskSubjectType subjectType, @Param("subjectKey") String subjectKey, @Param("eventCode") VelocityEventCode eventCode, @Param("from") Instant from, @Param("to") Instant to);

    /** 合计锚定主体在窗口内与指定币种相同的事件金额。 */
    BigDecimal sumAmount(@Param("subjectType") RiskSubjectType subjectType, @Param("subjectKey") String subjectKey, @Param("eventCode") VelocityEventCode eventCode, @Param("from") Instant from, @Param("to") Instant to, @Param("currency") String currency);

    /** 统计锚定主体在窗口内关联过的不同去重主体数。 */
    long countDistinct(@Param("subjectType") RiskSubjectType subjectType, @Param("subjectKey") String subjectKey, @Param("eventCode") VelocityEventCode eventCode, @Param("from") Instant from, @Param("to") Instant to, @Param("distinctType") RiskSubjectType distinctType);

    /** 读取锚定主体在窗口内最近一次事件。 */
    VelocityEvent selectLast(@Param("subjectType") RiskSubjectType subjectType, @Param("subjectKey") String subjectKey, @Param("eventCode") VelocityEventCode eventCode, @Param("from") Instant from, @Param("to") Instant to);
}
