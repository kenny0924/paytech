package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;

/** DecisionPoint 单表读取。 */
public interface DecisionPointRepository {
    /** 批量读取准确 ID 对应的本表事实。 */
    List<DecisionPoint> findByIds(List<Long> ids);
}
