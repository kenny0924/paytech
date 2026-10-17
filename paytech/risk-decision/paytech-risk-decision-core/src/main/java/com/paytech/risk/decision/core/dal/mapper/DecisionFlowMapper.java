package com.paytech.risk.decision.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowNodeRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEdgeRow;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;

/** 决策流固定表映射；写锁和共享锁只在外层已开启的本地事务中使用。 */
public interface DecisionFlowMapper {
    DecisionFlowRow findRoot(@Param("id") long id);
    DecisionFlowRow lockRoot(@Param("id") long id);
    DecisionFlowRow findByCode(@Param("pointId") long pointId, @Param("code") String code);
    List<DecisionFlowRow> listRoots(@Param("pointId") long pointId, @Param("code") String code,
        @Param("pattern") String pattern, @Param("offset") long offset, @Param("limit") int limit);
    long countRoots(@Param("pointId") long pointId, @Param("code") String code, @Param("pattern") String pattern);
    DecisionFlowVersionRow findVersion(@Param("id") long id);
    List<DecisionFlowVersionRow> findVersions(@Param("ids") List<Long> ids);
    List<DecisionFlowVersionRow> history(@Param("flowId") long flowId, @Param("maxVersionNo") int maxVersionNo,
        @Param("offset") long offset, @Param("limit") int limit);
    long countHistory(@Param("flowId") long flowId, @Param("maxVersionNo") int maxVersionNo);
    List<DecisionFlowNodeRow> nodes(@Param("versionId") long versionId);
    List<DecisionFlowEdgeRow> edges(@Param("versionId") long versionId);
    /** 当前列表页准确版本的规则集节点数。 */
    record NodeCount(long flowVersionId, int nodeCount) { }
    List<NodeCount> ruleSetNodeCounts(@Param("ids") List<Long> ids);
    int insertDecisionFlow(DecisionFlowRow row);
    int insertDecisionFlowVersion(DecisionFlowVersionRow row);
    int insertDecisionFlowNode(DecisionFlowNodeRow row);
    int insertDecisionFlowEdge(DecisionFlowEdgeRow row);
    int advance(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
        @Param("versionId") long versionId, @Param("time") Instant time);
    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
        @Param("actorId") long actorId, @Param("time") Instant time);
    /** 按调用方提供的稳定升序逐个获取共享根锁，再获取准确版本锁。 */
    RuleSet shareRuleSet(@Param("id") long id);
    RuleSetVersion shareRuleVersion(@Param("id") long id);
    List<com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet> legacyBindings(@Param("pointVersionId") long pointVersionId);
    int ruleCount(@Param("versionId") long versionId);
}
