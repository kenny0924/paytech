package com.paytech.risk.decision.domain.runtime;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次正式结账评估需要原子保存的全部事实；技术失败时只保存已有的轨迹，不写累计事件。 */
@Getter
@AllArgsConstructor
public class CheckoutRiskAssessmentContext {
    /** 正式评估。 */
    private final DecisionEvaluation evaluation;

    /** 结账输入快照。 */
    private final CheckoutInputSnapshot snapshot;

    /** Feature 结果，按执行顺序。 */
    private final List<EvaluationFeatureResult> featureResults;

    /** 规则与谓词轨迹。 */
    private final List<EvaluationRuleTrace> ruleTraces;

    /** 经过的流程节点。 */
    private final List<EvaluationFlowTrace> flowTraces;

    /** 累计事件；只在评估成功时非空。 */
    private final List<VelocityEvent> velocityEvents;

    /** 累计事件主体。 */
    private final List<VelocityEventSubject> velocitySubjects;
}
