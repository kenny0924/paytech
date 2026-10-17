package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEndOutput;

/** 结束节点附加输出的单表持久化边界，不开启事务。 */
public interface DecisionFlowEndOutputRepository {
    /** 插入一项输出，失败交由调用方回滚整个保存。 */
    void insert(DecisionFlowEndOutput row);

    /** 读取准确流程版本的全部输出。 */
    List<DecisionFlowEndOutput> findByVersion(long flowVersionId);
}
