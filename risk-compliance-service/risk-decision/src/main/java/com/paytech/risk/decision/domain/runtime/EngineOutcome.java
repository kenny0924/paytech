package com.paytech.risk.decision.domain.runtime;

import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.domain.enums.DecisionValue;

/**
 * 一次流程执行的结论与可解释轨迹
 *
 * @param decision
 *            结束节点给出的业务决定
 * @param reasonCode
 *            结束节点原因码；拒绝以外为空
 * @param outputs
 *            附加输出；放行时为决策点声明的全部输出（结束节点配置值或合同默认值），其他结论为空
 * @param ruleTraces
 *            实际执行的规则，按执行顺序
 * @param predicateTraces
 *            实际求值的规则条件谓词，按执行顺序
 * @param flowSteps
 *            实际经过的流程节点，按经过顺序；最后一个为结束节点
 */
public record EngineOutcome(DecisionValue decision, String reasonCode, Map<String, String> outputs,
    List<RuleTrace> ruleTraces, List<PredicateTrace> predicateTraces, List<FlowStep> flowSteps) {

    /**
     * 一条规则的执行结果
     *
     * @param ruleSetVersionId
     *            规则集版本 ID
     * @param ruleId
     *            规则 ID
     * @param matched
     *            条件是否成立
     * @param terminal
     *            命中且以终态动作结束所在规则集
     */
    public record RuleTrace(long ruleSetVersionId, long ruleId, boolean matched, boolean terminal) { }

    /**
     * 一个规则条件谓词的求值结果
     *
     * @param ruleId
     *            所属规则 ID
     * @param nodeId
     *            条件节点 ID
     * @param result
     *            求值结果
     */
    public record PredicateTrace(long ruleId, long nodeId, boolean result) { }

    /**
     * 经过的一个流程节点
     *
     * @param nodeKey
     *            流程版本内的节点键
     * @param nodeType
     *            节点类型：START、RULE_SET、END
     * @param ruleSetVersionId
     *            规则集节点引用的规则集版本；其他节点为空
     * @param matched
     *            规则集节点是否有规则命中；其他节点为空
     * @param decision
     *            规则集节点首个终态命中的决定（没有为空），或结束节点的结论
     * @param reused
     *            规则集节点是否复用了同一版本此前的结果；其他节点为空
     * @param exitEdgeKey
     *            离开本节点所走的连线；结束节点为空
     */
    public record FlowStep(String nodeKey, String nodeType, Long ruleSetVersionId, Boolean matched, String decision,
        Boolean reused, String exitEdgeKey) { }
}
