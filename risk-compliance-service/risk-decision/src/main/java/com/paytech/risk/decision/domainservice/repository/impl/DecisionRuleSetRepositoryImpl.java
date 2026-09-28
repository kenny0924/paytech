package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.ActionParameterDefinition;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointFeature;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointInputField;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointObligation;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.FeatureDependency;
import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;
import com.paytech.risk.decision.core.dal.dataobject.RiskRule;
import com.paytech.risk.decision.core.dal.dataobject.RuleAction;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;
import com.paytech.risk.decision.core.dal.dataobject.RuleNode;
import com.paytech.risk.decision.core.dal.dataobject.RuleOperand;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetReleaseReference;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;

/** 基于分表批量读取和显式受影响行校验的 Decision Rule Set 仓储。 */
@Repository
@lombok.AllArgsConstructor
public class DecisionRuleSetRepositoryImpl implements DecisionRuleSetRepository {

    private static final int MAX_RULES = 200;
    private static final int MAX_NODES = 2_000;
    private static final int MAX_OPERANDS = 10_000;
    private static final int MAX_FEATURE_ROWS = 2_000;

    private final DecisionRuleSetMapper mapper;

    /** 只读定位整体根，不过滤终态。 */
    @Override
    public Optional<RuleSet> findRuleSet(long ruleSetId) {
        return Optional.ofNullable(mapper.selectRuleSet(ruleSetId));
    }

    /** 通过不可变坐标查找整体根。 */
    @Override
    public Optional<RuleSet> findRuleSet(long pointId, String code) {
        return Optional.ofNullable(mapper.selectRuleSetByCode(pointId, code));
    }

    /** 在调用方已取得决策点锁后锁定当前根。 */
    @Override
    public Optional<RuleSet> lockRuleSet(long pointId, String code) {
        return Optional.ofNullable(mapper.lockRuleSet(pointId, code));
    }

    /** 保留所有左连接投影，包括异常槽位和缺失引用。 */
    @Override
    public List<RuleSetReleaseReference> releaseReferences(List<Long> pointIds, List<Long> ruleSetIds, boolean lock) {
        if (pointIds.isEmpty() || ruleSetIds.isEmpty()) {
            return List.of();
        }
        return safe(mapper.selectReleaseReferences(pointIds, ruleSetIds, lock), "活动发布配置引用");
    }

    /** 沿用事件点主键索引定位锁资源，不重新解释配置内容。 */
    @Override
    public Optional<DecisionPoint> findPoint(long decisionPointId) {
        return Optional.ofNullable(mapper.selectDecisionPointById(decisionPointId));
    }

    @Override
    public Optional<DecisionPoint> findPointByCode(String pointCode) {
        Objects.requireNonNull(pointCode, "pointCode");
        return Optional.ofNullable(mapper.selectDecisionPointByCode(pointCode));
    }

    @Override
    public Optional<RuleSetVersion> findVersion(long ruleSetVersionId) {
        return Optional.ofNullable(mapper.selectRuleSetVersion(ruleSetVersionId));
    }

    @Override
    public DecisionPoint lockPoint(long decisionPointId) {
        return requireLocked(mapper.lockDecisionPoint(decisionPointId),
            "决策事件点不存在: decisionPointId=" + decisionPointId);
    }

    @Override
    public Optional<RuleSetVersion> lockCurrent(long decisionPointId, String ruleSetCode) {
        Objects.requireNonNull(ruleSetCode, "ruleSetCode");
        return Optional.ofNullable(mapper.lockRuleSetCodeRange(decisionPointId, ruleSetCode));
    }

    @Override
    public RuleSetVersion lockVersion(long ruleSetVersionId) {
        return requireLocked(mapper.lockRuleSetVersion(ruleSetVersionId),
            "规则集版本不存在: ruleSetVersionId=" + ruleSetVersionId);
    }

    @Override
    public DecisionPointVersion lockCompanion(long pointVersionId) {
        return requireLocked(mapper.lockPointVersion(pointVersionId),
            "事件点版本不存在: pointVersionId=" + pointVersionId);
    }

    @Override
    public DecisionRuleSetAggregate requireClosure(long ruleSetVersionId) {
        return loadRuleSet(ruleSetVersionId, true);
    }

    @Override
    public DecisionRuleSetAggregate requireConfiguration(long ruleSetVersionId) {
        return loadRuleSet(ruleSetVersionId, false);
    }

    private DecisionRuleSetAggregate loadRuleSet(long ruleSetVersionId, boolean legacy) {
        RuleSetVersion ruleSet = requireFound(mapper.selectRuleSetVersion(ruleSetVersionId),
            "规则集版本不存在: ruleSetVersionId=" + ruleSetVersionId);
        DecisionPoint point = mapper.selectDecisionPointById(ruleSet.decisionPointId());
        if (point == null || point.decisionPointId() != ruleSet.decisionPointId()) {
            throw corruption("规则集版本引用的决策事件点缺失或错配");
        }

        List<DecisionPointRuleSet> companions = safe(
            mapper.selectCompanionBindingsByRuleSetVersionIds(List.of(ruleSetVersionId)), "Companion 绑定");
        uniqueIds(companions, DecisionPointRuleSet::bindingId, "Companion 绑定");
        if (companions.size() > 1) throw corruption("规则集版本存在多个旧编排绑定");
        if (legacy && companions.isEmpty()) {
            throw corruption("历史规则集缺少旧编排绑定");
        }
        DecisionPointVersion pointVersion = null;
        if (!companions.isEmpty()) {
            DecisionPointRuleSet companion = companions.getFirst();
            pointVersion = mapper.selectPointVersion(companion.pointVersionId());
            if (companion.ruleSetVersionId() != ruleSetVersionId || companion.sequenceNo() != 1
                || pointVersion == null || pointVersion.pointVersionId() != companion.pointVersionId()
                || pointVersion.decisionPointId() != point.decisionPointId()
                || pointVersion.status() != companionStatus(ruleSet.status())) {
                throw corruption("旧编排绑定的身份、归属或状态损坏");
            }
        }
        List<DecisionPointInputField> inputFields = legacy
            ? safe(mapper.selectPointInputFields(pointVersion.pointVersionId()), "事件点输入字段") : List.of();
        List<DecisionPointRuleSet> pointRuleSets = legacy
            ? safe(mapper.selectPointRuleSets(pointVersion.pointVersionId()), "事件点规则集绑定") : companions;
        List<DecisionPointFeature> pointFeatures = legacy
            ? safe(mapper.selectPointFeatures(pointVersion.pointVersionId()), "事件点 Feature 绑定") : List.of();
        List<DecisionPointObligation> obligations = legacy
            ? safe(mapper.selectPointObligations(pointVersion.pointVersionId()), "事件点系统义务") : List.of();
        List<RiskRule> rules = safe(mapper.selectRules(ruleSetVersionId), "风险规则");
        ensureMaximum(rules.size(), MAX_RULES, "Rule");

        List<Long> ruleIds = ids(rules, RiskRule::ruleId);
        List<RuleNode> nodes = ruleIds.isEmpty() ? List.of() : safe(mapper.selectNodes(ruleIds), "规则节点");
        ensureMaximum(nodes.size(), MAX_NODES, "Node");
        List<Long> nodeIds = ids(nodes, RuleNode::nodeId);
        List<RuleOperand> operands = nodeIds.isEmpty()
            ? List.of() : safe(mapper.selectOperands(nodeIds), "规则操作数");
        ensureMaximum(operands.size(), MAX_OPERANDS, "Operand");
        List<RuleAction> ruleActions = ruleIds.isEmpty()
            ? List.of() : safe(mapper.selectRuleActions(ruleIds), "规则 Action");

        LinkedHashSet<Long> actionIds = new LinkedHashSet<>();
        ruleActions.forEach(value -> actionIds.add(value.actionVersionId()));
        obligations.stream().map(DecisionPointObligation::actionVersionId).filter(Objects::nonNull)
            .forEach(actionIds::add);
        List<ActionVersion> actionVersions = actionIds.isEmpty()
            ? List.of() : requireActionVersions(List.copyOf(actionIds));
        List<ActionParameterDefinition> definitions = actionIds.isEmpty() ? List.of()
            : safe(mapper.selectActionParameterDefinitions(List.copyOf(actionIds)), "Action 参数定义");
        List<Long> ruleActionIds = ids(ruleActions, RuleAction::ruleActionId);
        List<RuleActionParameter> parameters = ruleActionIds.isEmpty() ? List.of()
            : safe(mapper.selectRuleActionParameters(ruleActionIds), "规则 Action 参数");

        LinkedHashSet<Long> referencedFeatureIds = new LinkedHashSet<>();
        pointFeatures.forEach(value -> referencedFeatureIds.add(value.featureVersionId()));
        operands.stream().map(RuleOperand::featureVersionId).filter(Objects::nonNull)
            .forEach(referencedFeatureIds::add);
        parameters.stream().map(RuleActionParameter::featureVersionId).filter(Objects::nonNull)
            .forEach(referencedFeatureIds::add);
        if (!legacy && !referencedFeatureIds.isEmpty()) throw corruption("规则配置不支持 Feature 来源");
        FeatureClosure featureClosure = legacy ? loadFeatureClosure(referencedFeatureIds)
            : new FeatureClosure(List.of(), List.of());

        validateClosure(point, pointVersion, ruleSet, inputFields, pointFeatures, pointRuleSets,
            obligations, rules, nodes, operands, ruleActions, actionVersions, definitions, parameters,
            featureClosure.versions(), featureClosure.dependencies());
        return new DecisionRuleSetAggregate(point, pointVersion, inputFields, pointFeatures, pointRuleSets,
            obligations, ruleSet, rules, nodes, operands, ruleActions, actionVersions, definitions,
            parameters, featureClosure.versions(), featureClosure.dependencies());
    }

    @Override
    public List<ActionVersion> requireActionVersions(List<Long> actionVersionIds) {
        List<Long> requested = requireDistinctPositiveIds(actionVersionIds, "Action Version");
        List<ActionVersion> loaded = safe(mapper.selectActionVersions(requested), "Action Version");
        Map<Long, ActionVersion> byId = uniqueIds(loaded, ActionVersion::actionVersionId, "Action Version");
        requireExactIds(requested, byId.keySet(), "Action Version");
        return requested.stream().map(byId::get).toList();
    }

    @Override
    public void requireNoActionParameterDefinitions(List<Long> actionVersionIds) {
        List<Long> requested = requireDistinctPositiveIds(actionVersionIds, "Action Version");
        List<ActionParameterDefinition> definitions = safe(
            mapper.selectActionParameterDefinitions(requested), "Action 参数定义");
        if (!definitions.isEmpty()) {
            throw corruption("首期内置 Action 不得存在参数定义");
        }
    }

    @Override
    public List<ActionParameterDefinition> actionParameterDefinitions(List<Long> actionVersionIds) {
        return safe(mapper.selectActionParameterDefinitions(requireDistinctPositiveIds(actionVersionIds, "Action Version")),
            "Action 参数定义");
    }

    @Override
    public RuleSetPage findPage(RuleSetPageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.limit(), query.offset());
        if (mapper.countMalformedRuleSets(query.pointCode(), query.ruleSetCode()) != 0) {
            throw corruption("规则集管理根缺失或当前指针损坏，请核对迁移与历史配置");
        }
        List<RuleSetVersion> rows = safe(mapper.selectRuleSetVersions(query.pointCode(), query.ruleSetCode(),
            query.status(), query.limit(), query.offset()), "规则集分页");
        long total = mapper.countRuleSetVersions(query.pointCode(), query.ruleSetCode(), query.status());
        if (rows.isEmpty()) {
            return new RuleSetPage(List.of(), total);
        }

        Map<Long, RuleSetVersion> roots = uniqueIds(rows, RuleSetVersion::ruleSetVersionId, "规则集分页");
        LinkedHashSet<Long> pointIds = new LinkedHashSet<>();
        rows.forEach(value -> pointIds.add(value.decisionPointId()));
        List<DecisionPoint> points = safe(mapper.selectDecisionPointsByIds(List.copyOf(pointIds)), "决策事件点分页");
        Map<Long, DecisionPoint> pointsById = uniqueIds(points, DecisionPoint::decisionPointId, "决策事件点分页");
        requireExactIds(pointIds, pointsById.keySet(), "决策事件点分页");

        List<DecisionPointRuleSet> bindings = safe(mapper.selectCompanionBindingsByRuleSetVersionIds(
            List.copyOf(roots.keySet())), "规则集分页 Companion 绑定");
        uniqueIds(bindings, DecisionPointRuleSet::bindingId, "规则集分页 Companion 绑定");
        Map<Long, List<DecisionPointRuleSet>> byRuleSet = group(bindings, DecisionPointRuleSet::ruleSetVersionId);
        LinkedHashSet<Long> pointVersionIds = bindings.stream().map(DecisionPointRuleSet::pointVersionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<DecisionPointVersion> pointVersions = pointVersionIds.isEmpty() ? List.of() : safe(
            mapper.selectPointVersionsByIds(List.copyOf(pointVersionIds)), "规则集分页 Point Version");
        Map<Long, DecisionPointVersion> pointVersionsById = uniqueIds(pointVersions,
            DecisionPointVersion::pointVersionId, "规则集分页 Point Version");
        requireExactIds(pointVersionIds, pointVersionsById.keySet(), "规则集分页 Point Version");
        Map<Long, RuleSet> management = uniqueIds(safe(mapper.selectRuleSetsByCurrentVersions(
            List.copyOf(roots.keySet())), "规则集管理根"), RuleSet::getCurrentRuleSetVersionId, "当前配置入口");
        requireExactIds(roots.keySet(), management.keySet(), "当前配置入口");
        List<RuleSetSummary> items = new ArrayList<>(rows.size());
        for (RuleSetVersion row : rows) {
            DecisionPoint point = pointsById.get(row.decisionPointId());
            List<DecisionPointRuleSet> rowBindings = byRuleSet.getOrDefault(row.ruleSetVersionId(), List.of());
            if (point == null || rowBindings.size() > 1) {
                throw corruption("规则集分页闭包缺失 Point 或唯一 Companion 绑定");
            }
            DecisionPointRuleSet binding = rowBindings.isEmpty() ? null : rowBindings.getFirst();
            if (binding == null) {
                items.add(new RuleSetSummary(row, point, null, management.get(row.ruleSetVersionId())));
                continue;
            }
            DecisionPointVersion pointVersion = pointVersionsById.get(binding.pointVersionId());
            if (pointVersion == null || pointVersion.decisionPointId() != row.decisionPointId()
                || binding.sequenceNo() != 1 || pointVersion.status() != companionStatus(row.status())) {
                throw corruption("规则集分页 Companion Point Version 坐标或状态损坏");
            }
            items.add(new RuleSetSummary(row, point, binding, management.get(row.ruleSetVersionId())));
        }
        return new RuleSetPage(items, total);
    }

    private FeatureClosure loadFeatureClosure(Collection<Long> initialIds) {
        LinkedHashMap<Long, FeatureVersion> versions = new LinkedHashMap<>();
        LinkedHashMap<Long, FeatureDependency> dependencies = new LinkedHashMap<>();
        LinkedHashSet<Long> frontier = new LinkedHashSet<>(initialIds);
        while (!frontier.isEmpty()) {
            ensureMaximum(versions.size() + frontier.size(), MAX_FEATURE_ROWS, "Feature Version");
            List<Long> requested = List.copyOf(frontier);
            List<FeatureVersion> loaded = safe(mapper.selectFeatureVersions(requested), "Feature Version");
            Map<Long, FeatureVersion> batch = uniqueIds(loaded, FeatureVersion::featureVersionId,
                "Feature Version");
            requireExactIds(requested, batch.keySet(), "Feature Version");
            batch.forEach((id, value) -> {
                if (versions.putIfAbsent(id, value) != null) {
                    throw corruption("Feature Version 查询返回重复主键");
                }
            });

            List<FeatureDependency> loadedDependencies = safe(
                mapper.selectFeatureDependencies(requested), "Feature Dependency");
            for (FeatureDependency dependency : loadedDependencies) {
                if (!frontier.contains(dependency.featureVersionId())) {
                    throw corruption("Feature Dependency 返回了未请求的 Owner");
                }
                if (dependencies.putIfAbsent(dependency.dependencyId(), dependency) != null) {
                    throw corruption("Feature Dependency 查询返回重复主键");
                }
            }
            ensureMaximum(dependencies.size(), MAX_FEATURE_ROWS, "Feature Dependency");
            frontier = loadedDependencies.stream().map(FeatureDependency::dependencyFeatureVersionId)
                .filter(id -> !versions.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return new FeatureClosure(List.copyOf(versions.values()), List.copyOf(dependencies.values()));
    }

    private void validateClosure(
        DecisionPoint point,
        DecisionPointVersion pointVersion,
        RuleSetVersion ruleSet,
        List<DecisionPointInputField> inputFields,
        List<DecisionPointFeature> pointFeatures,
        List<DecisionPointRuleSet> pointRuleSets,
        List<DecisionPointObligation> obligations,
        List<RiskRule> rules,
        List<RuleNode> nodes,
        List<RuleOperand> operands,
        List<RuleAction> ruleActions,
        List<ActionVersion> actionVersions,
        List<ActionParameterDefinition> definitions,
        List<RuleActionParameter> parameters,
        List<FeatureVersion> featureVersions,
        List<FeatureDependency> featureDependencies
    ) {
        Map<Long, DecisionPointInputField> fields = uniqueIds(inputFields,
            DecisionPointInputField::inputFieldId, "事件点输入字段");
        uniqueCoordinates(inputFields, DecisionPointInputField::fieldCode, "事件点字段编码");
        inputFields.forEach(value -> require(value.pointVersionId() == pointVersion.pointVersionId(),
            "输入字段跨 Point Version"));

        uniqueIds(pointRuleSets, DecisionPointRuleSet::bindingId, "事件点规则集绑定");
        uniqueCoordinates(pointRuleSets, value -> Long.toString(value.ruleSetVersionId()),
            "事件点规则集版本绑定");
        uniqueCoordinates(pointRuleSets, value -> Integer.toString(value.sequenceNo()),
            "事件点规则集顺序");
        pointRuleSets.forEach(value -> require(value.pointVersionId() == pointVersion.pointVersionId(),
            "规则集绑定跨 Point Version"));
        if (pointVersion != null && (pointRuleSets.size() != 1 || pointRuleSets.getFirst().ruleSetVersionId() != ruleSet.ruleSetVersionId())) {
            throw corruption("首期 Point Version 必须仅绑定当前 Rule Set Version");
        }

        uniqueIds(pointFeatures, DecisionPointFeature::bindingId, "事件点 Feature 绑定");
        uniqueCoordinates(pointFeatures, value -> Long.toString(value.featureVersionId()), "Feature 绑定");
        uniqueCoordinates(pointFeatures, value -> Integer.toString(value.sequenceNo()), "Feature 绑定顺序");
        pointFeatures.forEach(value -> require(value.pointVersionId() == pointVersion.pointVersionId(),
            "Feature 绑定跨 Point Version"));

        Map<Long, DecisionPointObligation> obligationById = uniqueIds(obligations,
            DecisionPointObligation::obligationId, "系统义务");
        uniqueCoordinates(obligations,
            value -> value.triggerDecision().getCode() + ':' + value.sequenceNo(), "系统义务顺序");
        obligations.forEach(value -> require(value.pointVersionId() == pointVersion.pointVersionId(),
            "系统义务跨 Point Version"));

        Map<Long, RiskRule> rulesById = uniqueIds(rules, RiskRule::ruleId, "风险规则");
        uniqueCoordinates(rules, RiskRule::ruleCode, "规则编码");
        uniqueCoordinates(rules, value -> Integer.toString(value.priorityNo()), "规则优先级");
        rules.forEach(value -> require(value.ruleSetVersionId() == ruleSet.ruleSetVersionId(),
            "Rule 跨 Rule Set Version"));

        Map<Long, RuleNode> nodesById = uniqueIds(nodes, RuleNode::nodeId, "规则节点");
        uniqueCoordinates(nodes, value -> value.ruleId() + ":" + value.parentNodeId() + ':' + value.sequenceNo(),
            "规则节点同级顺序");
        for (RuleNode node : nodes) {
            require(rulesById.containsKey(node.ruleId()), "Node 引用了闭包外 Rule");
            if (node.parentNodeId() != null) {
                RuleNode parent = nodesById.get(node.parentNodeId());
                require(parent != null && parent.ruleId() == node.ruleId(), "Node Parent 缺失或跨 Rule");
            }
        }

        uniqueIds(operands, RuleOperand::operandId, "规则操作数");
        uniqueCoordinates(operands,
            value -> value.nodeId() + ":" + value.operandRole().getCode() + ':' + value.valueSequence(),
            "规则操作数坐标");
        operands.forEach(value -> {
            require(nodesById.containsKey(value.nodeId()), "Operand 引用了闭包外 Node");
            if (value.fieldCode() != null) {
                require(inputFields.isEmpty() || inputFields.stream().anyMatch(field -> field.fieldCode().equals(value.fieldCode())), "Operand 引用了闭包外 Input Field");
            }
        });

        Map<Long, RuleAction> actionsById = uniqueIds(ruleActions, RuleAction::ruleActionId, "规则 Action");
        uniqueCoordinates(ruleActions, value -> value.ruleId() + ":" + value.sequenceNo(), "规则 Action 顺序");
        Map<Long, ActionVersion> versionsById = uniqueIds(actionVersions,
            ActionVersion::actionVersionId, "Action Version");
        uniqueCoordinates(actionVersions, value -> value.actionCode() + ':' + value.versionNo(),
            "Action Version 业务坐标");
        ruleActions.forEach(value -> {
            require(rulesById.containsKey(value.ruleId()), "Rule Action 引用了闭包外 Rule");
            require(versionsById.containsKey(value.actionVersionId()), "Rule Action 引用了缺失 Action Version");
        });
        obligationById.values().stream().map(DecisionPointObligation::actionVersionId)
            .filter(Objects::nonNull).forEach(id ->
                require(versionsById.containsKey(id), "Obligation 引用了缺失 Action Version"));

        Map<Long, ActionParameterDefinition> definitionsById = uniqueIds(definitions,
            ActionParameterDefinition::parameterDefinitionId, "Action 参数定义");
        uniqueCoordinates(definitions, value -> value.actionVersionId() + ":" + value.parameterCode(),
            "Action 参数定义编码");
        definitions.forEach(value -> require(versionsById.containsKey(value.actionVersionId()),
            "参数定义引用了闭包外 Action Version"));
        uniqueIds(parameters, RuleActionParameter::parameterId, "规则 Action 参数");
        uniqueCoordinates(parameters,
            value -> value.ruleActionId() + ":" + value.parameterDefinitionId() + ':' + value.valueSequence(),
            "规则 Action 参数坐标");
        for (RuleActionParameter parameter : parameters) {
            RuleAction action = actionsById.get(parameter.ruleActionId());
            ActionParameterDefinition definition = definitionsById.get(parameter.parameterDefinitionId());
            require(action != null && definition != null, "规则 Action 参数引用缺失");
            require(action.actionVersionId() == definition.actionVersionId(),
                "规则 Action 参数定义属于其他 Action Version");
            if (parameter.fieldCode() != null) {
                require(inputFields.isEmpty() || inputFields.stream().anyMatch(field -> field.fieldCode().equals(parameter.fieldCode())), "规则 Action 参数引用闭包外 Input Field");
            }
        }

        Map<Long, FeatureVersion> featuresById = uniqueIds(featureVersions,
            FeatureVersion::featureVersionId, "Feature Version");
        uniqueCoordinates(featureVersions, value -> value.featureCode() + ':' + value.versionNo(),
            "Feature Version 业务坐标");
        pointFeatures.forEach(value -> require(featuresById.containsKey(value.featureVersionId()),
            "Point Feature 引用了缺失 Feature Version"));
        operands.stream().map(RuleOperand::featureVersionId).filter(Objects::nonNull)
            .forEach(id -> require(featuresById.containsKey(id), "Operand 引用了缺失 Feature Version"));
        parameters.stream().map(RuleActionParameter::featureVersionId).filter(Objects::nonNull)
            .forEach(id -> require(featuresById.containsKey(id), "Action 参数引用了缺失 Feature Version"));

        uniqueIds(featureDependencies, FeatureDependency::dependencyId, "Feature Dependency");
        uniqueCoordinates(featureDependencies,
            value -> value.featureVersionId() + ":" + value.dependencyFeatureVersionId(),
            "Feature Dependency 目标");
        uniqueCoordinates(featureDependencies,
            value -> value.featureVersionId() + ":" + value.sequenceNo(), "Feature Dependency 顺序");
        featureDependencies.forEach(value -> require(featuresById.containsKey(value.featureVersionId())
            && featuresById.containsKey(value.dependencyFeatureVersionId()),
            "Feature Dependency 引用了闭包外版本"));

        require(ruleSet.decisionPointId() == point.decisionPointId(), "Rule Set 跨 Decision Point");
        if (pointVersion != null) require(pointVersion.decisionPointId() == point.decisionPointId(), "Point Version 跨 Decision Point");
    }

    private static <T> Map<Long, T> uniqueIds(List<T> values, ToLongFunction<T> id, String label) {
        LinkedHashMap<Long, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) {
                throw corruption(label + " 包含 null 行");
            }
            long key = id.applyAsLong(value);
            if (indexed.putIfAbsent(key, value) != null) {
                throw corruption(label + " 包含重复主键");
            }
        }
        return Map.copyOf(indexed);
    }

    private static <T> void uniqueCoordinates(List<T> values, Function<T, String> coordinate, String label) {
        Set<String> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(coordinate.apply(value))) {
                throw corruption(label + " 包含重复业务坐标");
            }
        }
    }

    private static <T> Map<Long, List<T>> group(List<T> values, ToLongFunction<T> key) {
        Map<Long, List<T>> grouped = new HashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(key.applyAsLong(value), ignored -> new ArrayList<>()).add(value);
        }
        grouped.replaceAll((ignored, list) -> List.copyOf(list));
        return Map.copyOf(grouped);
    }

    private static List<Long> requireDistinctPositiveIds(List<Long> values, String label) {
        requireBatch(values, label);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null || value <= 0 || !ids.add(value)) {
                throw corruption(label + " ID 集合为空、重复或非正数");
            }
        }
        return List.copyOf(ids);
    }

    private static <T> List<Long> ids(List<T> values, ToLongFunction<T> id) {
        return values.stream().map(id::applyAsLong).toList();
    }

    private static void requireExactIds(Collection<Long> requested, Collection<Long> loaded, String label) {
        if (!new LinkedHashSet<>(requested).equals(new LinkedHashSet<>(loaded))) {
            throw corruption(label + " 查询结果缺失或包含额外主键");
        }
    }

    private static <T> T requireFound(T value, String message) {
        if (value == null) {
            throw new NoSuchElementException(message);
        }
        return value;
    }

    private static <T> T requireLocked(T value, String message) {
        if (value == null) {
            throw corruption("锁中资源消失或引用损坏: " + message);
        }
        return value;
    }

    private static <T> List<T> safe(List<T> values, String label) {
        if (values == null) {
            throw corruption(label + " Mapper 返回 null");
        }
        return List.copyOf(values);
    }

    private static void requirePage(int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页 limit/offset 非法");
        }
    }

    private static void ensureMaximum(int actual, int maximum, String label) {
        if (actual > maximum) {
            throw corruption(label + " 闭包超过首期工程上限");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw corruption(message);
        }
    }

    private static void requireAffected(int actual, int expected, String operation) {
        if (actual != expected) {
            throw corruption(operation + " 受影响行数异常: expected=" + expected + ", actual=" + actual);
        }
    }

    private static void requireCas(int actual, String operation) {
        if (actual != 1) {
            throw corruption(operation + "受影响行数异常: actual=" + actual);
        }
    }

    private static DecisionPointVersionStatus companionStatus(RuleSetVersionStatus status) {
        return switch (status) {
            case DRAFT -> DecisionPointVersionStatus.DRAFT;
            case FROZEN -> DecisionPointVersionStatus.FROZEN;
            case RETIRED -> DecisionPointVersionStatus.RETIRED;
        };
    }

    private static <T> void requireBatch(List<T> values, String label) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " 批量参数不能为空或包含 null");
        }
    }

    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    private record FeatureClosure(List<FeatureVersion> versions, List<FeatureDependency> dependencies) {
    }
}
