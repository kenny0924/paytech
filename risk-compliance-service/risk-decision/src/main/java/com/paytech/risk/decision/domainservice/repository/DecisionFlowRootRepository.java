package com.paytech.risk.decision.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;

/** 流程管理根表的持久化边界，不开启事务。 */
public interface DecisionFlowRootRepository {
    /** 按原请求身份读取根，供锁前解析稳定资源。 */
    Optional<DecisionFlowRow> findById(long id);

    /** 事务内按原主键读取并锁定管理根。 */
    Optional<DecisionFlowRow> lockById(long id);

    /** 按决策点和永久编码读取根。 */
    Optional<DecisionFlowRow> findByCode(long pointId, String code);

    /** 原版本条件写入成功后同步根的指针和修订号。 */
    void advance(DecisionFlowRow root, DecisionFlowVersionRow version);

    /** 条件删除成功后同步原根的删除事实。 */
    void delete(DecisionFlowRow root, long actorId, Instant time);

    /** 插入一条已构造事实，失败交由调用方回滚整个保存。 */
    void insert(DecisionFlowRow value);

    /** 按根和当前名称筛选，每个流程只返回一行。 */
    List<DecisionFlowRow> list(long pointId, String flowCode, String keyword, long offset, int limit);

    /** 与列表使用完全相同的筛选条件。 */
    long count(long pointId, String flowCode, String keyword);
}
