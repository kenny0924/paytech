package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;

/** 旧点配置所属单表的只读查询，不开启事务。 */
public interface DecisionPointRuleSetRepository {
    /** 读取准确历史事实；绑定查询保留既有 201 条防超限边界。 */
    List<DecisionPointRuleSet> findLegacyBindings(long pointVersionId);
    /** 批量读取准确 ID 对应的本表事实。 */
    List<DecisionPointRuleSet> findByPointVersionIds(List<Long> ids);
    /** 读取冻结发布的全部绑定，不截断历史组成。 */
    List<DecisionPointRuleSet> findByPointVersion(long id);
}
