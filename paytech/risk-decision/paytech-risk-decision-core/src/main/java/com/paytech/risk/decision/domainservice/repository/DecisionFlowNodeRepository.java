package com.paytech.risk.decision.domainservice.repository;

import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowNodeRow;

/** 流程节点表的持久化边界，不开启事务。 */
public interface DecisionFlowNodeRepository {
    /** 读取准确版本的全部节点。 */
    List<DecisionFlowNodeRow> findByVersion(long versionId);

    /** 插入一条已构造事实，失败交由调用方回滚整个保存。 */
    void insert(DecisionFlowNodeRow value);

    /** 批量统计当前页准确版本的规则集节点数。 */
    Map<Long, Integer> ruleSetNodeCounts(List<Long> versionIds);
}
