package com.paytech.risk.decision.domain.runtime;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedDailyTotal;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import com.paytech.risk.decision.core.dal.dataobject.VelocityEventSubject;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次正式支付评估需要原子保存的全部事实；技术失败时没有累计事件，只保存已有的 Feature 结果与轨迹。 */
@Getter
@AllArgsConstructor
public class PaymentRiskAssessmentContext {
    /** 正式评估。 */
    private final DecisionEvaluation evaluation;

    /** 交易评估输入快照。 */
    private final TransactionRiskAssessment assessment;

    /** Feature 结果。 */
    private final List<EvaluationFeatureResult> featureResults;

    /** 规则与谓词轨迹。 */
    private final List<EvaluationRuleTrace> ruleTraces;

    /** 经过的流程节点。 */
    private final List<EvaluationFlowTrace> flowTraces;

    /** 本次写入的累计事件。 */
    private final List<VelocityEvent> velocityEvents;

    /** 累计事件关联的主体。 */
    private final List<VelocityEventSubject> velocitySubjects;

    /** Client 当日放行汇总增量；只在放行时有一条。 */
    private final List<ClientAllowedDailyTotal> dailyTotals;
}
