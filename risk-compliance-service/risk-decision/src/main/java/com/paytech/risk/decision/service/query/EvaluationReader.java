package com.paytech.risk.decision.service.query;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanations;
import com.paytech.risk.decision.domain.evaluation.EvaluationView;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domainservice.repository.DecisionEvaluationRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowRootRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionFlowVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationFlowTraceRepository;
import com.paytech.risk.decision.domainservice.repository.EvaluationRuleTraceRepository;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import com.paytech.risk.decision.service.evaluation.DecisionPointEvaluator;
import com.paytech.risk.decision.service.evaluation.EvaluationSubject;

import lombok.AllArgsConstructor;

/**
 * 读取评估结论与解释；只读，不开启事务
 * <li>评估及其快照、轨迹在同一事务中一次写入后不再修改，分多条 SQL 读取不会观察到不一致的组合。</li>
 * <li>输入快照与业务身份由该风控点的评估器提供；影子评估取其基线正式评估的输入。</li>
 */
@Component
@AllArgsConstructor
public class EvaluationReader {
    /** 解释中最多列出的影子评估数。 */
    static final int SHADOW_LIMIT = 20;

    private final ExecutionSnapshotReader snapshots;
    private final List<DecisionPointEvaluator> evaluators;
    private final DecisionRuleSetRepository points;
    private final ExecutionReleaseRepository releases;
    private final DecisionFlowRootRepository flowRoots;
    private final DecisionEvaluationRepository evaluations;
    private final DecisionFlowVersionRepository flowVersions;
    private final EvaluationFlowTraceRepository flowTraces;
    private final EvaluationRuleTraceRepository ruleTraces;
    private final EvaluationFeatureResultRepository featureResults;

    /**
     * 按评估 ID 读取结论
     *
     * @param evaluationId 评估 ID
     * @return 结论与坐标；评估不存在返回 404
     */
    public EvaluationView evaluation(long evaluationId) {
        Facts facts = facts(evaluationId);
        DecisionFlow.Graph graph = snapshots.graph(facts.point().pointCode(), facts.release().getDecisionFlowVersionId());
        return view(facts, graph);
    }

    /**
     * 按风控点与业务身份读取正式评估的结论
     *
     * @param pointCode 风控点编码；未登记评估器视为参数错误
     * @param businessKey 业务身份；不符合该点格式视为参数错误
     * @return 结论与坐标；该业务身份没有评估返回 404
     */
    public EvaluationView primary(String pointCode, String businessKey) {
        DecisionPointEvaluator evaluator = evaluator(pointCode);
        if (!evaluator.contract().businessKeyPattern().matcher(businessKey).matches()) {
            throw new InvalidParameterException("业务身份格式不符合风控点合同: " + pointCode);
        }
        long evaluationId = evaluator.findPrimaryEvaluationId(businessKey)
            .orElseThrow(() -> new FailException(CommonErrorCode.NOT_FOUND, "该业务身份没有评估: " + pointCode));
        return evaluation(evaluationId);
    }

    /**
     * 读取评估解释
     *
     * @param evaluationId 评估 ID
     * @return 输入、路径、规则、变量与影子比较；评估不存在返回 404
     */
    public EvaluationExplanationView explanation(long evaluationId) {
        Facts facts = facts(evaluationId);
        ExecutionSnapshot snapshot = snapshots.read(facts.release());
        DecisionEvaluation evaluation = facts.evaluation();
        List<DecisionEvaluation> shadows = evaluation.getEvaluationMode() == EvaluationMode.PRIMARY
            ? evaluations.findByBaseline(evaluationId, SHADOW_LIMIT) : List.of();
        return new EvaluationExplanationView(evaluationId, facts.subject().inputs(), !facts.path().isEmpty(),
            EvaluationExplanations.path(facts.path(), snapshot),
            EvaluationExplanations.rules(ruleTraces.findByEvaluation(evaluationId), snapshot),
            EvaluationExplanations.variables(snapshot, facts.subject().contractValues(),
                featureResults.findByEvaluation(evaluationId)),
            EvaluationExplanations.shadows(shadows));
    }

    /** 评估、发布、风控点、冻结输入与流程轨迹。 */
    Facts facts(long evaluationId) {
        DecisionEvaluation evaluation = evaluations.findById(evaluationId)
            .orElseThrow(() -> new FailException(CommonErrorCode.NOT_FOUND, "评估不存在: " + evaluationId));
        ExecutionRelease release = releases.findById(evaluation.getReleaseId())
            .filter(value -> value.getDecisionFlowVersionId() != null)
            .orElseThrow(() -> new ErrorException("评估的执行发布不存在: " + evaluationId));
        DecisionPoint point = points.findPoint(release.getDecisionPointId())
            .orElseThrow(() -> new ErrorException("评估的决策点不存在: " + evaluationId));
        long primaryId = evaluation.getEvaluationMode() == EvaluationMode.SHADOW ? evaluation.getBaselineEvaluationId()
            : evaluationId;
        EvaluationSubject subject = evaluator(point.pointCode()).subject(primaryId);
        return new Facts(evaluation, release, point, subject, flowTraces.findByEvaluation(evaluationId));
    }

    /** 组装结论；原因与影子输出取自到达的结束节点，正式评估的输出取已保存的值。 */
    EvaluationView view(Facts facts, DecisionFlow.Graph graph) {
        DecisionEvaluation evaluation = facts.evaluation();
        DecisionFlowVersionRow version = flowVersions.findById(facts.release().getDecisionFlowVersionId())
            .orElseThrow(() -> new ErrorException("评估的决策流版本不存在: " + evaluation.getEvaluationId()));
        DecisionFlowRow flow = flowRoots.findById(version.flowId())
            .orElseThrow(() -> new ErrorException("评估的决策流不存在: " + evaluation.getEvaluationId()));
        DecisionFlow.Node end = succeeded(evaluation) ? endNode(facts.path(), graph) : null;
        return new EvaluationView(evaluation.getEvaluationId(), facts.point().pointCode(), facts.subject().businessKey(),
            evaluation.getEvaluationMode(), EvaluationExplanations.result(evaluation),
            end == null ? null : end.reasonCode(), end == null ? null : end.reasonMessage(), outputs(facts, end),
            evaluation.getFailureCode(), facts.release().getReleaseId(), facts.release().getEnvironmentCode(),
            flow.flowId(), flow.flowCode(), version.flowVersionId(), version.versionNo(),
            evaluation.getEvaluationTime(), evaluation.getElapsedMillis(), evaluation.getBaselineEvaluationId(),
            evaluation.getShadowRunId(), !facts.path().isEmpty());
    }

    /** 放行时的附加输出：正式评估取已保存的值，影子评估取结束节点配置。 */
    static Map<String, String> outputs(Facts facts, DecisionFlow.Node end) {
        if (!succeeded(facts.evaluation()) || facts.evaluation().getBusinessDecision() != DecisionValue.ALLOW) {
            return Map.of();
        }
        if (facts.evaluation().getEvaluationMode() == EvaluationMode.PRIMARY) {
            return facts.subject().outputs();
        }
        return end == null ? Map.of() : end.outputs();
    }

    /** 轨迹最后一行的结束节点；没有路径为空。 */
    static DecisionFlow.Node endNode(List<EvaluationFlowTrace> path, DecisionFlow.Graph graph) {
        if (path.isEmpty() || path.getLast().getNodeType() != FlowNodeType.END) {
            return null;
        }
        String key = path.getLast().getNodeKey();
        return graph.nodes().stream().filter(node -> node.nodeKey().equals(key)).findFirst()
            .orElseThrow(() -> new ErrorException("评估轨迹的结束节点不在流程版本中: " + key));
    }

    /** 技术上是否成功。 */
    static boolean succeeded(DecisionEvaluation evaluation) {
        return evaluation.getTechnicalStatus() == TechnicalStatus.SUCCEEDED;
    }

    /** 风控点的评估器；未登记视为参数错误。 */
    DecisionPointEvaluator evaluator(String pointCode) {
        return evaluators.stream().filter(value -> value.contract().pointCode().equals(pointCode)).findFirst()
            .orElseThrow(() -> new InvalidParameterException("风控点未登记评估器: " + pointCode));
    }

    /**
     * 一次评估的基础事实
     *
     * @param evaluation 评估
     * @param release 执行发布
     * @param point 风控点
     * @param subject 冻结的业务身份、输入与输出
     * @param path 流程轨迹；没有记录为空
     */
    record Facts(DecisionEvaluation evaluation, ExecutionRelease release, DecisionPoint point, EvaluationSubject subject,
        List<EvaluationFlowTrace> path) { }
}
