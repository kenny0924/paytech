package com.paytech.risk.decision.domain.runtime;

import java.util.List;

import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;

/**
 * 一次流程执行的结论与可解释轨迹
 *
 * @param decision 结束节点给出的业务决定
 * @param reasonCode 结束节点原因码；拒绝以外为空
 * @param authenticationRequirement 认证要求；仅放行时非空
 * @param ruleTraces 实际执行的规则，按执行顺序
 * @param predicateTraces 实际求值的规则条件谓词，按执行顺序
 */
public record EngineOutcome(DecisionValue decision, String reasonCode,
    AuthenticationRequirement authenticationRequirement, List<RuleTrace> ruleTraces,
    List<PredicateTrace> predicateTraces) {

    /**
     * 一条规则的执行结果
     *
     * @param ruleSetVersionId 规则集版本 ID
     * @param ruleId 规则 ID
     * @param matched 条件是否成立
     * @param terminal 命中且以终态动作结束所在规则集
     */
    public record RuleTrace(long ruleSetVersionId, long ruleId, boolean matched, boolean terminal) { }

    /**
     * 一个规则条件谓词的求值结果
     *
     * @param ruleId 所属规则 ID
     * @param nodeId 条件节点 ID
     * @param result 求值结果
     */
    public record PredicateTrace(long ruleId, long nodeId, boolean result) { }
}
