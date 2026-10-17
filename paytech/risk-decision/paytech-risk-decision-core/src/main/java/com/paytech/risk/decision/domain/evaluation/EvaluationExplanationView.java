package com.paytech.risk.decision.domain.evaluation;

import java.util.List;
import java.util.Map;

/**
 * 一次评估的解释：冻结输入、流程路径、执行过的规则、变量取值与影子比较
 *
 * @param evaluationId 评估 ID
 * @param inputs 冻结输入原值，按合同字段顺序；影子评估取基线评估的输入
 * @param pathAvailable 是否记录了流程路径；本需求上线前的评估为否
 * @param path 经过的节点，按经过顺序
 * @param rules 执行过的规则，按执行顺序
 * @param variables 发布引用的变量取值（不含节点输出），按变量编码排序
 * @param shadows 以本评估为基线的影子评估；影子评估本身为空
 */
public record EvaluationExplanationView(long evaluationId, Map<String, String> inputs, boolean pathAvailable,
    List<Step> path, List<Rule> rules, List<Variable> variables, List<Shadow> shadows) {

    /**
     * 经过的一个节点
     *
     * @param stepNo 经过顺序，从 1 开始
     * @param nodeKey 节点键
     * @param nodeType 节点类型
     * @param nodeName 节点名称
     * @param ruleSetVersionId 规则集节点引用的规则集版本；其他节点为空
     * @param ruleSetCode 规则集编码；同上
     * @param ruleSetVersionNo 规则集版本号；同上
     * @param matched 规则集节点是否有规则命中；其他节点为空
     * @param decision 规则集节点首个终态命中的决定，或结束节点的结论
     * @param reused 规则集节点是否复用了同一版本此前的结果；其他节点为空
     * @param exitEdgeKey 离开本节点所走的连线键；结束节点为空
     * @param exitEdgeName 该连线的名称；未命名为空
     */
    public record Step(int stepNo, String nodeKey, String nodeType, String nodeName, Long ruleSetVersionId,
        String ruleSetCode, Integer ruleSetVersionNo, Boolean matched, String decision, Boolean reused,
        String exitEdgeKey, String exitEdgeName) { }

    /**
     * 一条执行过的规则
     *
     * @param ruleSetVersionId 所属规则集版本
     * @param ruleId 规则 ID
     * @param ruleCode 规则编码
     * @param ruleName 规则名称
     * @param priorityNo 优先级
     * @param matched 条件是否成立
     * @param terminal 是否以终态动作结束所在规则集
     * @param action 终态动作编码；没有动作为空
     * @param predicates 实际求值的条件谓词，按求值顺序
     */
    public record Rule(long ruleSetVersionId, long ruleId, String ruleCode, String ruleName, int priorityNo,
        boolean matched, boolean terminal, String action, List<Predicate> predicates) { }

    /**
     * 一个条件谓词的求值结果；条件结构以规则集版本详情为准
     *
     * @param nodeId 条件节点 ID
     * @param result 求值结果
     */
    public record Predicate(long nodeId, boolean result) { }

    /**
     * 一个变量的取值
     *
     * @param variableCode 变量编码
     * @param variableName 变量名称
     * @param sourceType 来源
     * @param valueType 值类型
     * @param value 标量规范值；没有值或集合为空
     * @param values 集合当前成员；非集合为空
     * @param valueBasis EVALUATION（评估时的值）或 CURRENT（当前值，可能与评估时不同）
     * @param technicalStatus Feature 技术状态；非 Feature 为空
     * @param failureCode Feature 失败代码；成功或非 Feature 为空
     * @param elapsedMillis Feature 计算耗时；非 Feature 为空
     */
    public record Variable(String variableCode, String variableName, String sourceType, String valueType, String value,
        List<String> values, String valueBasis, String technicalStatus, String failureCode, Integer elapsedMillis) { }

    /**
     * 一个影子评估的比较结果
     *
     * @param evaluationId 影子评估 ID
     * @param shadowRunId 影子批次 ID
     * @param result 影子结论或技术状态
     * @param decisionChanged 结论是否变化
     * @param outputChanged 附加输出（认证要求）是否变化
     * @param severity 变化严重程度
     */
    public record Shadow(long evaluationId, long shadowRunId, String result, boolean decisionChanged,
        boolean outputChanged, String severity) { }
}
