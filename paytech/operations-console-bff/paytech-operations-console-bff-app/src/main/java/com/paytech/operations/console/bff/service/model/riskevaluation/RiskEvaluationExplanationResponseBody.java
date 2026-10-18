package com.paytech.operations.console.bff.service.model.riskevaluation;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 风控评估解释页面模型
 * <li>输入、路径、变量与影子比较原样透传 Risk 解释；规则在 Risk 规则轨迹基础上补充 BFF 组装的条件树。</li>
 * <li>输入含 IP、User-Agent、卡指纹、消费者引用等受限原值，所在路径只记元数据日志。</li>
 */
@Getter
@Setter
public class RiskEvaluationExplanationResponseBody {
    /**
     * 评估 ID
     */
    private String evaluationId;

    /**
     * 评估时冻结的输入原值；未提供的字段为 null
     */
    private Map<String, String> inputs;

    /**
     * 是否记录了流程路径
     */
    private boolean pathAvailable;

    /**
     * 流程路径，按经过顺序；未记录时为空数组
     */
    private List<RiskEvaluationPathStep> path;

    /**
     * 实际执行过的规则，按执行顺序
     */
    private List<RiskEvaluationRuleItem> rules;

    /**
     * 发布引用的变量取值
     */
    private List<RiskEvaluationVariableItem> variables;

    /**
     * 以本评估为基线的影子评估；仅正式评估有值
     */
    private List<RiskEvaluationShadowItem> shadows;
}
