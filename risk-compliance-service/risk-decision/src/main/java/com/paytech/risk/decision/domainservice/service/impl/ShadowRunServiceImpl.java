package com.paytech.risk.decision.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
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
    private final EvaluationFeatureResultRepository featureResults;

    /** 评估与 Feature 结果同一事务提交。 */
    @Override
    public void saveSample(DecisionEvaluation evaluation, List<EvaluationFeatureResult> featureResults) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            evaluations.insert(evaluation);
            this.featureResults.insertAll(featureResults);
        });
    }

    /** 单行条件更新。 */
    @Override
    public void complete(ReleaseShadowRun run, long expectedVersion) {
        shadowRuns.complete(run, expectedVersion);
    }
}
