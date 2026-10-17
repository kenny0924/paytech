package com.paytech.risk.decision.api.evaluation.response;

import java.util.List;

/** 评估中执行过的一条规则。 */
public class EvaluationRuleItem {

    /** 所属规则集版本 ID，十进制字符串。 */
    private String ruleSetVersionId;

    /** 规则 ID，十进制字符串。 */
    private String ruleId;

    /** 规则编码。 */
    private String ruleCode;

    /** 规则名称。 */
    private String ruleName;

    /** 优先级，越小越先执行。 */
    private int priorityNo;

    /** 条件是否成立。 */
    private boolean matched;

    /** 是否以终态动作结束所在规则集。 */
    private boolean terminal;

    /**
     * 终态动作编码。
     * <li>没有配置动作为空。</li>
     */
    private String action;

    /** 实际求值的条件谓词结果，按求值顺序。 */
    private List<EvaluationPredicateItem> predicates;

    /** 返回所属规则集版本 ID。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置所属规则集版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

    /** 返回规则 ID。 */
    public String getRuleId() { return ruleId; }

    /** 设置规则 ID。 */
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    /** 返回规则编码。 */
    public String getRuleCode() { return ruleCode; }

    /** 设置规则编码。 */
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    /** 返回规则名称。 */
    public String getRuleName() { return ruleName; }

    /** 设置规则名称。 */
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    /** 返回优先级。 */
    public int getPriorityNo() { return priorityNo; }

    /** 设置优先级。 */
    public void setPriorityNo(int priorityNo) { this.priorityNo = priorityNo; }

    /** 返回条件是否成立。 */
    public boolean isMatched() { return matched; }

    /** 设置条件是否成立。 */
    public void setMatched(boolean matched) { this.matched = matched; }

    /** 返回是否以终态动作结束所在规则集。 */
    public boolean isTerminal() { return terminal; }

    /** 设置是否以终态动作结束所在规则集。 */
    public void setTerminal(boolean terminal) { this.terminal = terminal; }

    /** 返回终态动作编码。 */
    public String getAction() { return action; }

    /** 设置终态动作编码。 */
    public void setAction(String action) { this.action = action; }

    /** 返回实际求值的条件谓词结果。 */
    public List<EvaluationPredicateItem> getPredicates() { return predicates; }

    /** 设置实际求值的条件谓词结果。 */
    public void setPredicates(List<EvaluationPredicateItem> predicates) { this.predicates = predicates; }
}
