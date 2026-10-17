package com.paytech.risk.decision.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.decision.domain.runtime.CheckoutRiskAssessmentContext;
import com.paytech.risk.decision.domainservice.repository.CheckoutInputSnapshotRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFlowTraceRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationRuleTraceRepository;
import com.paytech.risk.decision.domainservice.repository.VelocityEventRepository;
import com.paytech.risk.decision.domainservice.repository.VelocityEventSubjectRepository;
import com.paytech.risk.decision.domainservice.service.CheckoutRiskAssessmentService;

import lombok.AllArgsConstructor;

/** 正式结账评估的本地事务保存；事务内只操作 Risk 数据库。 */
@Service
@AllArgsConstructor
public class CheckoutRiskAssessmentServiceImpl implements CheckoutRiskAssessmentService {
    private final PlatformTransactionManager transactionManager;
    private final DecisionEvaluationRepository evaluations;
    private final EvaluationFlowTraceRepository flowTraces;
    private final EvaluationRuleTraceRepository traces;
    private final CheckoutInputSnapshotRepository snapshots;
    private final EvaluationFeatureResultRepository featureResults;
    private final VelocityEventRepository velocityEvents;
    private final VelocityEventSubjectRepository velocitySubjects;

    /** 评估先于依附它的快照、结果、轨迹与累计事件写入，全部在一个事务中提交。 */
    @Override
    public void save(CheckoutRiskAssessmentContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            evaluations.insert(context.getEvaluation());
            snapshots.insert(context.getSnapshot());
            featureResults.insertAll(context.getFeatureResults());
            traces.insertAll(context.getRuleTraces());
            flowTraces.insertAll(context.getFlowTraces());
            velocityEvents.insertAll(context.getVelocityEvents());
            velocitySubjects.insertAll(context.getVelocitySubjects());
        });
    }
}
