package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEdgeRow;

/** 流程连线表的持久化边界，不开启事务。 */
public interface DecisionFlowEdgeRepository {
    /** 读取准确版本的全部连线。 */
    List<DecisionFlowEdgeRow> findByVersion(long versionId);

    /** 插入一条已构造事实，失败交由调用方回滚整个保存。 */
    void insert(DecisionFlowEdgeRow value);
}
