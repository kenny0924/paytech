package com.paytech.risk.decision.domain.runtime;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个影子样本需要原子保存的事实；与正式评估相同，失败或超时只保存已有的 Feature 结果与轨迹。 */
@Getter
@AllArgsConstructor
public class ShadowSampleContext {
    /** 影子评估。 */
    private final DecisionEvaluation evaluation;

    /** Feature 结果。 */
    private final List<EvaluationFeatureResult> featureResults;

    /** 规则与谓词轨迹。 */
    private final List<EvaluationRuleTrace> ruleTraces;

    /** 经过的流程节点。 */
    private final List<EvaluationFlowTrace> flowTraces;
}
