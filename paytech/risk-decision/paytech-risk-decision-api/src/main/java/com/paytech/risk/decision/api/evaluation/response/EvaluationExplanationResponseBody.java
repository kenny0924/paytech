package com.paytech.risk.decision.api.evaluation.response;

import java.util.List;
import java.util.Map;

/** 一次评估的解释：冻结输入、流程路径、执行过的规则、变量取值与影子比较。 */
public class EvaluationExplanationResponseBody {

    /** 评估 ID，十进制字符串。 */
    private String evaluationId;

    /**
     * 评估冻结的全部输入字段原值，按合同字段顺序。
     * <li>含 IP、User-Agent、卡指纹、消费者引用等受限值，调用方不得写入日志；未提供的字段为 null；影子评估取基线评估的输入。</li>
     */
    private Map<String, String> inputs;

    /**
     * 是否记录了流程路径。
     * <li>本功能上线前的评估为 false，path 为空数组。</li>
     */
    private boolean pathAvailable;

    /** 经过的流程节点，按经过顺序；最后一个为结束节点。 */
    private List<EvaluationPathStep> path;

    /**
     * 执行过的规则，按执行顺序。
     * <li>条件结构用 ruleSetVersionId 查规则集版本详情。</li>
     */
    private List<EvaluationRuleItem> rules;

    /**
     * 发布引用的变量取值，按变量编码排序；不含节点输出。
     * <li>valueBasis 为 CURRENT 的变量是当前值，页面须标注可能与评估时不同。</li>
     */
    private List<EvaluationVariableItem> variables;

    /**
     * 以本评估为基线的影子评估，新的在前，最多 20 条。
     * <li>影子评估本身为空数组。</li>
     */
    private List<EvaluationShadowItem> shadows;

    /** 返回评估 ID。 */
    public String getEvaluationId() { return evaluationId; }

    /** 设置评估 ID。 */
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    /** 返回评估冻结的全部输入字段原值。 */
    public Map<String, String> getInputs() { return inputs; }

    /** 设置评估冻结的全部输入字段原值。 */
    public void setInputs(Map<String, String> inputs) { this.inputs = inputs; }

    /** 返回是否记录了流程路径。 */
    public boolean isPathAvailable() { return pathAvailable; }

    /** 设置是否记录了流程路径。 */
    public void setPathAvailable(boolean pathAvailable) { this.pathAvailable = pathAvailable; }

    /** 返回经过的流程节点。 */
    public List<EvaluationPathStep> getPath() { return path; }

    /** 设置经过的流程节点。 */
    public void setPath(List<EvaluationPathStep> path) { this.path = path; }

    /** 返回执行过的规则。 */
    public List<EvaluationRuleItem> getRules() { return rules; }

    /** 设置执行过的规则。 */
    public void setRules(List<EvaluationRuleItem> rules) { this.rules = rules; }

    /** 返回发布引用的变量取值。 */
    public List<EvaluationVariableItem> getVariables() { return variables; }

    /** 设置发布引用的变量取值。 */
    public void setVariables(List<EvaluationVariableItem> variables) { this.variables = variables; }

    /** 返回以本评估为基线的影子评估。 */
    public List<EvaluationShadowItem> getShadows() { return shadows; }

    /** 设置以本评估为基线的影子评估。 */
    public void setShadows(List<EvaluationShadowItem> shadows) { this.shadows = shadows; }
}
