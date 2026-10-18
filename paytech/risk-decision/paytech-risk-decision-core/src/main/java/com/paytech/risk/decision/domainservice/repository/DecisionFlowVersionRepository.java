package com.paytech.risk.decision.domainservice.repository;

import java.util.List;
import java.util.Optional;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;

/** 流程版本头表的持久化边界，不开启事务。 */
public interface DecisionFlowVersionRepository {
    /** 读取准确不可变版本头。 */
    Optional<DecisionFlowVersionRow> findById(long id);

    /** 插入一条已构造事实，失败交由调用方回滚整个保存。 */
    void insert(DecisionFlowVersionRow value);

    /** 批量读取当前页根引用的准确版本。 */
    List<DecisionFlowVersionRow> findByIds(List<Long> ids);

    /** 固定版本上界内倒序读取一页。 */
    List<DecisionFlowVersionRow> history(long flowId, int maxVersionNo, long offset, int limit);

    /** 固定版本上界内的版本总数。 */
    long historyCount(long flowId, int maxVersionNo);
}
