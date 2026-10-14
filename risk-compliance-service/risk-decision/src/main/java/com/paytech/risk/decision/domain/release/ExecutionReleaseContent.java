package com.paytech.risk.decision.domain.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.flow.DecisionFlow;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.InputFieldProjection;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.runtime.ExecutionSnapshot;
import com.paytech.risk.decision.domain.runtime.RuntimeHashes;
import com.paytech.risk.decision.domain.variable.RiskVariable;

import tools.jackson.databind.json.JsonMapper;

/**
 * 执行发布的内容摘要与静态校验；纯内存计算
 * <li>摘要覆盖决策点版本、决策流图、规则集版本摘要和 Feature Version；变量当前值不进入摘要。</li>
 * <li>静态校验沿用八项检查编码，按执行发布的语义判断。</li>
 */
public final class ExecutionReleaseContent {
    /** 执行发布内容摘要合同。 */
    static final String SCHEMA = "CODE-MOR:RISK:EXECUTION_RELEASE:1";

    /** 八项检查的固定顺序。 */
    public static final List<StaticCheckCode> CHECK_ORDER = List.of(StaticCheckCode.REFERENCE_INTEGRITY,
        StaticCheckCode.AST_SHAPE, StaticCheckCode.AST_TYPES, StaticCheckCode.FEATURE_DEPENDENCY,
        StaticCheckCode.HANDLER_HASH, StaticCheckCode.ACTION_PARAMETERS, StaticCheckCode.TERMINAL_ACTION,
        StaticCheckCode.SYSTEM_OBLIGATION);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯函数工具。 */
    private ExecutionReleaseContent() { }

    /**
     * 决策点版本的规范投影；执行发布的点版本不绑定规则集，由决策流引用
     *
     * @param descriptor 决策点合同
     * @param versionNo 点版本号
     * @return 投影
     */
    public static DecisionPointVersionProjection pointVersion(DecisionPointAuthoringDescriptor descriptor, int versionNo) {
        var point = descriptor.point();
        return new DecisionPointVersionProjection(point.pointCode(), DecisionBusinessDomain.valueOf(point.businessDomain()),
            versionNo, point.inputContractCode(), point.inputContractVersion(), point.outputContractCode(),
            point.defaultDecision(), point.evaluationStrategy(), point.failureStrategy(), point.timeoutMillis(),
            descriptor.inputFields().stream().map(field -> new InputFieldProjection(field.fieldCode(), field.valueType(),
                field.requiredFlag(), field.dataClassification(), field.ruleReferenceFlag(), field.actionParameterFlag()))
                .toList(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * 发布内容摘要
     *
     * @param pointVersionHash 决策点版本摘要
     * @param snapshot 执行内容
     * @return SHA-256 小写十六进制
     */
    public static String hash(String pointVersionHash, ExecutionSnapshot snapshot) {
        Map<String, String> values = new TreeMap<>();
        values.put("pointCode", snapshot.descriptor().point().pointCode());
        values.put("pointVersionId", Long.toString(snapshot.release().getPointVersionId()));
        values.put("pointVersionHash", pointVersionHash);
        values.put("flowVersionId", Long.toString(snapshot.release().getDecisionFlowVersionId()));
        values.put("flowGraph", JSON.writeValueAsString(snapshot.graph()));
        values.put("ruleSets", snapshot.ruleSets().values().stream().sorted((a, b) -> Long.compare(
            a.version().ruleSetVersionId(), b.version().ruleSetVersionId()))
            .map(set -> set.version().ruleSetVersionId() + "=" + set.version().contentHash())
            .collect(Collectors.joining(";")));
        values.put("features", snapshot.variables().values().stream().map(RiskVariable.Entry::definition)
            .filter(RiskVariable.Definition::featureBacked).map(d -> d.variableCode() + "=" + d.featureVersionId())
            .sorted().collect(Collectors.joining(";")));
        return RuntimeHashes.sha256(SCHEMA, values);
    }

    /**
     * 八项静态校验
     *
     * @param snapshot 执行内容；读取失败时为空
     * @param loadFailure 读取失败的安全说明；读取成功时为空
     * @param featureStatuses 引用的 Feature Version 状态
     * @return 按固定顺序的八项结果
     */
    public static List<StaticCheckResult> check(ExecutionSnapshot snapshot, String loadFailure,
        Map<Long, ExecutionVersionStatus> featureStatuses) {
        if (snapshot == null) {
            List<StaticCheckResult> results = new ArrayList<>();
            for (StaticCheckCode code : CHECK_ORDER) {
                results.add(code == StaticCheckCode.REFERENCE_INTEGRITY ? failed(code, loadFailure)
                    : failed(code, "依赖的发布内容无法读取"));
            }
            return results;
        }
        return List.of(referenceIntegrity(snapshot), astShape(snapshot), passed(StaticCheckCode.AST_TYPES),
            featureDependency(snapshot, featureStatuses), handlerHash(snapshot), actionParameters(snapshot),
            terminalAction(snapshot), passed(StaticCheckCode.SYSTEM_OBLIGATION));
    }

    /** 流程引用的规则集版本必须全部冻结。 */
    static StaticCheckResult referenceIntegrity(ExecutionSnapshot snapshot) {
        boolean frozen = snapshot.ruleSets().values().stream()
            .allMatch(set -> set.version().status() == RuleSetVersionStatus.FROZEN);
        return frozen ? passed(StaticCheckCode.REFERENCE_INTEGRITY)
            : failed(StaticCheckCode.REFERENCE_INTEGRITY, "流程引用的规则集版本必须已冻结");
    }

    /** 连线条件必须是统一变量格式。 */
    static StaticCheckResult astShape(ExecutionSnapshot snapshot) {
        boolean variableFormat = snapshot.graph().edges().stream().filter(edge -> edge.condition() != null)
            .allMatch(edge -> edge.condition().schemaVersion() == 2);
        return variableFormat ? passed(StaticCheckCode.AST_SHAPE)
            : failed(StaticCheckCode.AST_SHAPE, "流程连线条件必须使用统一变量格式");
    }

    /** 实时累计与名单命中变量的 Feature Version 必须启用且参数完整。 */
    static StaticCheckResult featureDependency(ExecutionSnapshot snapshot, Map<Long, ExecutionVersionStatus> statuses) {
        for (RiskVariable.Entry entry : snapshot.variables().values()) {
            RiskVariable.Definition d = entry.definition();
            if (!d.featureBacked()) {
                continue;
            }
            boolean defined = d.sourceType() == VariableSource.VELOCITY
                ? snapshot.velocityFeatures().containsKey(d.featureVersionId())
                : snapshot.listFeatures().containsKey(d.featureVersionId());
            if (!defined || statuses.get(d.featureVersionId()) != ExecutionVersionStatus.ACTIVE) {
                return failed(StaticCheckCode.FEATURE_DEPENDENCY, "Feature 未启用或参数缺失: " + d.variableCode());
            }
        }
        return passed(StaticCheckCode.FEATURE_DEPENDENCY);
    }

    /** 规则终态动作必须属于本决策点。 */
    static StaticCheckResult handlerHash(ExecutionSnapshot snapshot) {
        for (DecisionRuleSetAggregate set : snapshot.ruleSets().values()) {
            for (var rule : set.rules()) {
                if (rule.action() != null && !snapshot.descriptor().ownsAction(rule.action().action().actionCode(),
                    rule.action().action().versionNo())) {
                    return failed(StaticCheckCode.HANDLER_HASH, "规则动作不属于本决策点: " + rule.ruleCode());
                }
            }
        }
        return passed(StaticCheckCode.HANDLER_HASH);
    }

    /** 支付决策点一期不开放附加动作。 */
    static StaticCheckResult actionParameters(ExecutionSnapshot snapshot) {
        boolean none = snapshot.ruleSets().values().stream().flatMap(set -> set.rules().stream())
            .allMatch(rule -> rule.additionalActions().isEmpty());
        return none || snapshot.descriptor().additionalActionsSupported() ? passed(StaticCheckCode.ACTION_PARAMETERS)
            : failed(StaticCheckCode.ACTION_PARAMETERS, "规则包含本决策点不支持的附加动作");
    }

    /** 每个结束节点都给出业务决定。 */
    static StaticCheckResult terminalAction(ExecutionSnapshot snapshot) {
        boolean decided = snapshot.graph().nodes().stream().filter(node -> "END".equals(node.type()))
            .allMatch(node -> node.decisionCode() != null);
        boolean hasEnd = snapshot.graph().nodes().stream().map(DecisionFlow.Node::type).anyMatch("END"::equals);
        return decided && hasEnd ? passed(StaticCheckCode.TERMINAL_ACTION)
            : failed(StaticCheckCode.TERMINAL_ACTION, "流程结束节点必须给出业务决定");
    }

    /** 通过。 */
    static StaticCheckResult passed(StaticCheckCode code) {
        return new StaticCheckResult(code, StaticCheckStatus.PASSED, StaticCheckSeverity.INFO, null, null);
    }

    /** 失败，摘要不含输入值。 */
    static StaticCheckResult failed(StaticCheckCode code, String summary) {
        return new StaticCheckResult(code, StaticCheckStatus.FAILED, StaticCheckSeverity.ERROR, code.getCode() + "_FAILED",
            summary);
    }
}
