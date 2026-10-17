package com.paytech.risk.decision.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.runtime.ShadowSampleContext;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFlowTraceRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationRuleTraceRepository;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import com.paytech.risk.decision.domainservice.service.ShadowRunService;

import lombok.AllArgsConstructor;

/** 影子回放结果的本地事务保存。 */
@Service
@AllArgsConstructor
public class ShadowRunServiceImpl implements ShadowRunService {
    private final ReleaseShadowRunRepository shadowRuns;
    private final DecisionEvaluationRepository evaluations;
    private final PlatformTransactionManager transactionManager;
    private final EvaluationFlowTraceRepository flowTraces;
    private final EvaluationRuleTraceRepository ruleTraces;
    private final EvaluationFeatureResultRepository featureResults;

    /** 评估先于依附它的 Feature 结果与轨迹写入，同一事务提交。 */
    @Override
    public void saveSample(ShadowSampleContext context) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            evaluations.insert(context.getEvaluation());
            featureResults.insertAll(context.getFeatureResults());
            ruleTraces.insertAll(context.getRuleTraces());
            flowTraces.insertAll(context.getFlowTraces());
        });
    }

    /** 单行条件更新。 */
    @Override
    public void complete(ReleaseShadowRun run, long expectedVersion) {
        shadowRuns.complete(run, expectedVersion);
    }
}
