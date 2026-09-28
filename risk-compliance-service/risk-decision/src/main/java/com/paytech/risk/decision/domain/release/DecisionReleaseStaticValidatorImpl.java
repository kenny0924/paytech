package com.paytech.risk.decision.domain.release;

import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.BooleanValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ConditionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionReleaseProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.GroupProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.NullValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.OperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.PredicateProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseActionVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseInputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseNodeProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseOperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.StringValue;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.rule.CanonicalUtcMicrosecond;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 始终执行完八项检查，单项内收集并规范化全部违规。 */
public final class DecisionReleaseStaticValidatorImpl implements DecisionReleaseStaticValidator {

    private static final List<StaticCheckCode> ORDER = List.of(
        StaticCheckCode.REFERENCE_INTEGRITY, StaticCheckCode.AST_SHAPE, StaticCheckCode.AST_TYPES,
        StaticCheckCode.FEATURE_DEPENDENCY, StaticCheckCode.HANDLER_HASH, StaticCheckCode.ACTION_PARAMETERS,
        StaticCheckCode.TERMINAL_ACTION, StaticCheckCode.SYSTEM_OBLIGATION);

    @Override
    public List<StaticCheckResult> validate(DecisionReleaseClosure closure) {
        ArrayList<StaticCheckResult> results = new ArrayList<>();
        if (closure == null || closure.point() == null || closure.ruleSet() == null || closure.release() == null
            || closure.persistedActions() == null) {
            for (StaticCheckCode code : ORDER) {
                results.add(summarize(code, List.of(new StaticViolation("CLOSURE_INVALID", "closure", null, null, null))));
            }
            return List.copyOf(results);
        }
        if (com.paytech.risk.decision.domain.variable.VariableRuleConfiguration.CONTENT_FORMAT.equals(closure.ruleSet().contentFormat())) {
            return ORDER.stream().map(code -> summarize(code, List.of(new StaticViolation("CONFIGURATION_ONLY", "ruleSet", null, null, null)))).toList();
        }
        for (StaticCheckCode code : ORDER) {
            List<StaticViolation> violations;
            try {
                violations = switch (code) {
                    case REFERENCE_INTEGRITY -> references(closure);
                    case AST_SHAPE -> astShape(closure.release());
                    case AST_TYPES -> astTypes(closure.release());
                    case FEATURE_DEPENDENCY -> unsupported("FEATURE_UNSUPPORTED", "features",
                        !closure.release().features().isEmpty() || !closure.release().featureDependencies().isEmpty());
                    case HANDLER_HASH -> handlerHash(closure);
                    case ACTION_PARAMETERS -> unsupported("ACTION_PARAMETER_UNSUPPORTED", "actionParameters",
                        !closure.release().actionParameterDefinitions().isEmpty()
                            || !closure.release().ruleActionParameters().isEmpty());
                    case TERMINAL_ACTION -> terminalActions(closure.release());
                    case SYSTEM_OBLIGATION -> unsupported("SYSTEM_OBLIGATION_UNSUPPORTED", "obligations",
                        !closure.release().obligations().isEmpty() || !closure.release().limitBindings().isEmpty());
                };
            } catch (JsonCanonicalizationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                violations = List.of(new StaticViolation("CHECK_INPUT_INVALID", "closure", null, null, null));
            }
            results.add(summarize(code, violations));
        }
        return List.copyOf(results);
    }

    static StaticCheckResult summarize(StaticCheckCode code, List<StaticViolation> raw) {
        List<StaticViolation> values = raw.stream().map(DecisionReleaseStaticValidatorImpl::normalize)
            .sorted(VIOLATION_ORDER).distinct().toList();
        if (values.isEmpty()) {
            return new StaticCheckResult(code, StaticCheckStatus.PASSED, StaticCheckSeverity.INFO, null, null);
        }
        StaticViolation first = values.getFirst();
        String prefix = "first=" + first.errorCode() + "@" + (first.path() == null ? "-" : first.path());
        String suffix = "; violation_count=" + values.size();
        int budget = 1000 - suffix.length();
        if (prefix.length() > budget) prefix = prefix.substring(0, Math.max(0, budget));
        return new StaticCheckResult(code, StaticCheckStatus.FAILED, StaticCheckSeverity.ERROR,
            first.errorCode(), prefix + suffix);
    }

    private static List<StaticViolation> references(DecisionReleaseClosure closure) {
        DecisionReleaseProjection r = closure.release();
        ArrayList<StaticViolation> out = new ArrayList<>();
        RiskDecisionCanonicalizerImpl canonicalizer = new RiskDecisionCanonicalizerImpl();
        String expectedRuleSetHash = canonicalizer.ruleSetVersion(closure.ruleSet()).sha256LowerHex();
        String expectedPointHash = canonicalizer.decisionPointVersion(closure.point()).sha256LowerHex();
        CheckoutPrepareAuthoringDescriptor descriptor = CheckoutPrepareAuthoringDescriptor.v1();
        if (r.environmentCode() != DecisionEnvironment.TEST || r.releaseNo() <= 0) {
            add(out, "RELEASE_HEADER", "release", null, null, null);
        }
        if (!r.decisionPointId().equals(r.decisionPoint().decisionPointId())
            || !r.pointVersionId().equals(r.pointVersion().pointVersionId())
            || !r.decisionPointId().equals(r.pointVersion().decisionPointId())
            || !closure.point().pointCode().equals(r.decisionPoint().pointCode())
            || closure.point().businessDomain() != r.decisionPoint().businessDomain()
            || !closure.point().pointCode().equals(r.pointVersion().pointCode())
            || closure.point().pointVersionNo() != r.pointVersion().pointVersionNo()
            || !closure.point().inputContractCode().equals(r.pointVersion().inputContractCode())
            || closure.point().inputContractVersion() != r.pointVersion().inputContractVersion()
            || !closure.point().outputContractCode().equals(r.pointVersion().outputContractCode())
            || closure.point().defaultDecision() != r.pointVersion().defaultDecision()
            || closure.point().evaluationStrategy() != r.pointVersion().evaluationStrategy()
            || closure.point().failureStrategy() != r.pointVersion().failureStrategy()
            || closure.point().timeoutMillis() != r.pointVersion().timeoutMillis()
            || !expectedPointHash.equals(r.pointVersion().contentHash())) {
            add(out, "RELEASE_POINT_REF", "release", null, null, null);
        }
        if (!descriptor.point().pointCode().equals(closure.point().pointCode())
            || !descriptor.point().businessDomain().equals(closure.point().businessDomain().getCode())
            || !descriptor.point().inputContractCode().equals(closure.point().inputContractCode())
            || descriptor.point().inputContractVersion() != closure.point().inputContractVersion()
            || !descriptor.point().outputContractCode().equals(closure.point().outputContractCode())
            || descriptor.point().defaultDecision() != closure.point().defaultDecision()
            || descriptor.point().evaluationStrategy() != closure.point().evaluationStrategy()
            || descriptor.point().failureStrategy() != closure.point().failureStrategy()
            || descriptor.point().timeoutMillis() != closure.point().timeoutMillis()) {
            add(out, "POINT_DIRECTORY_REF", "point", null, null, null);
        }
        if (closure.point().inputFields().size() != descriptor.fields().size() || r.inputFields().size() != descriptor.fields().size())
            add(out, "INPUT_FIELD_CLOSURE", "inputFields", null, null, null);
        for (CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor expected : descriptor.fields()) {
            boolean pointMatch = closure.point().inputFields().stream().anyMatch(actual -> inputMatches(expected, actual));
            boolean releaseMatch = r.inputFields().stream().anyMatch(actual -> inputMatches(expected, actual)
                && r.pointVersionId().equals(actual.pointVersionId()));
            if (!pointMatch || !releaseMatch) add(out, "INPUT_FIELD_DIRECTORY", "inputFields/" + expected.fieldCode(), null, null, null);
        }
        if (closure.point().ruleSets().size() != 1 || r.ruleSetBindings().size() != 1 || r.ruleSetVersions().size() != 1)
            add(out, "RULE_SET_CLOSURE", "ruleSets", null, null, null);
        else {
            var binding = closure.point().ruleSets().getFirst();
            var releaseBinding = r.ruleSetBindings().getFirst();
            var releaseRuleSet = r.ruleSetVersions().getFirst();
            if (binding.sequenceNo() != 1 || releaseBinding.sequenceNo() != 1
                || !closure.ruleSet().pointCode().equals(closure.point().pointCode())
                || !closure.ruleSet().ruleSetCode().equals(binding.ruleSetCode())
                || closure.ruleSet().versionNo() != binding.ruleSetVersionNo()
                || !expectedRuleSetHash.equals(binding.ruleSetContentHash())
                || !releaseBinding.ruleSetVersionId().equals(releaseRuleSet.ruleSetVersionId())
                || !r.pointVersionId().equals(releaseBinding.pointVersionId())
                || !r.decisionPointId().equals(releaseRuleSet.decisionPointId())
                || !closure.ruleSet().ruleSetCode().equals(releaseRuleSet.ruleSetCode())
                || closure.ruleSet().versionNo() != releaseRuleSet.versionNo()
                || !expectedRuleSetHash.equals(releaseRuleSet.contentHash())) {
                add(out, "RULE_SET_CLOSURE", "ruleSets", null, null, null);
            }
        }
        for (String topId : List.of(r.releaseId(), r.decisionPointId(), r.pointVersionId(),
            r.decisionPoint().decisionPointId(), r.pointVersion().decisionPointId(), r.pointVersion().pointVersionId())) {
            if (!validId(topId)) add(out, "INVALID_ID", "release", null, null, null);
        }
        Set<String> fieldIds = ids(r.inputFields(), RiskDecisionCanonicalizer.ReleaseInputFieldProjection::inputFieldId,
            out, "DUPLICATE_INPUT_FIELD");
        Set<String> ruleSetIds = ids(r.ruleSetVersions(), RiskDecisionCanonicalizer.ReleaseRuleSetVersionProjection::ruleSetVersionId,
            out, "DUPLICATE_RULE_SET_VERSION");
        Set<String> ruleIds = ids(r.rules(), RiskDecisionCanonicalizer.ReleaseRuleProjection::ruleId, out, "DUPLICATE_RULE");
        Set<String> nodeIds = ids(r.nodes(), RiskDecisionCanonicalizer.ReleaseNodeProjection::nodeId, out, "DUPLICATE_NODE");
        Set<String> actionIds = ids(r.actionVersions(), RiskDecisionCanonicalizer.ReleaseActionVersionProjection::actionVersionId,
            out, "DUPLICATE_ACTION_VERSION");
        ids(r.ruleSetBindings(), RiskDecisionCanonicalizer.ReleaseRuleSetBindingProjection::bindingId, out, "DUPLICATE_BINDING");
        ids(r.operands(), RiskDecisionCanonicalizer.ReleaseOperandProjection::operandId, out, "DUPLICATE_OPERAND");
        ids(r.ruleActions(), RiskDecisionCanonicalizer.ReleaseRuleActionProjection::ruleActionId, out, "DUPLICATE_RULE_ACTION");
        duplicateBusinessCoordinates(r, out);
        r.ruleSetBindings().forEach(v -> { if (!r.pointVersionId().equals(v.pointVersionId()) || !ruleSetIds.contains(v.ruleSetVersionId())) add(out, "RULE_SET_BINDING_REF", "ruleSetBindings", null, null, null); });
        r.rules().forEach(v -> { if (!ruleSetIds.contains(v.ruleSetVersionId())) add(out, "RULE_REF", "rules", v.ruleCode(), id(v.ruleId()), null); });
        r.nodes().forEach(v -> {
            if (!ruleIds.contains(v.ruleId()) || v.parentNodeId() != null && !nodeIds.contains(v.parentNodeId()))
                add(out, "NODE_REF", "nodes", null, id(v.nodeId()), null);
        });
        r.operands().forEach(v -> {
            if (!nodeIds.contains(v.nodeId()) || v.inputFieldId() != null && !fieldIds.contains(v.inputFieldId()))
                add(out, "OPERAND_REF", "operands", null, id(v.nodeId()), null);
        });
        r.ruleActions().forEach(v -> {
            if (!ruleIds.contains(v.ruleId()) || !actionIds.contains(v.actionVersionId()))
                add(out, "RULE_ACTION_REF", "ruleActions", null, null, null);
        });
        Set<String> usedActions = r.ruleActions().stream().map(ReleaseRuleActionProjection::actionVersionId)
            .collect(java.util.stream.Collectors.toSet());
        r.actionVersions().forEach(v -> {
            if (!usedActions.contains(v.actionVersionId())) {
                add(out, "ORPHAN_ACTION_VERSION", "actionVersions/" + v.actionVersionId(), null, null,
                    v.actionCode());
            }
        });
        if (!releaseSemanticallyMatchesRuleSet(closure)) {
            add(out, "RULE_SET_SEMANTIC_CLOSURE", "ruleSets", null, null, null);
        }
        return out;
    }

    private static void duplicateBusinessCoordinates(DecisionReleaseProjection release,
                                                     List<StaticViolation> out) {
        duplicates(release.inputFields(), ReleaseInputFieldProjection::fieldCode, out, "DUPLICATE_FIELD_CODE");
        duplicates(release.rules(), ReleaseRuleProjection::ruleCode, out, "DUPLICATE_RULE_CODE");
        duplicates(release.rules(), value -> Integer.toString(value.priorityNo()), out,
            "DUPLICATE_RULE_PRIORITY");
        duplicates(release.ruleSetVersions(), value -> value.ruleSetCode() + ":" + value.versionNo(), out,
            "DUPLICATE_RULE_SET_COORDINATE");
        duplicates(release.actionVersions(), value -> value.actionCode() + ":" + value.versionNo(), out,
            "DUPLICATE_ACTION_COORDINATE");
    }

    private static <T> void duplicates(List<T> rows, Function<T, String> coordinate,
                                       List<StaticViolation> out, String code) {
        Set<String> seen = new HashSet<>();
        for (T row : rows) {
            String value = row == null ? null : coordinate.apply(row);
            if (value == null || !seen.add(value)) add(out, code, "businessCoordinates", null, null, null);
        }
    }

    private static boolean releaseSemanticallyMatchesRuleSet(DecisionReleaseClosure closure) {
        DecisionReleaseProjection release = closure.release();
        RuleSetVersionProjection expected = closure.ruleSet();
        if (release.rules().size() != expected.rules().size()) return false;
        Map<String, RuleProjection> expectedRules = index(expected.rules(), RuleProjection::ruleCode);
        Map<String, ReleaseInputFieldProjection> fields = index(release.inputFields(),
            ReleaseInputFieldProjection::inputFieldId);
        Map<String, ReleaseActionVersionProjection> actions = index(release.actionVersions(),
            ReleaseActionVersionProjection::actionVersionId);
        Map<String, List<ReleaseNodeProjection>> nodes = new HashMap<>();
        release.nodes().forEach(value -> nodes.computeIfAbsent(value.ruleId(), ignored -> new ArrayList<>()).add(value));
        Map<String, List<ReleaseOperandProjection>> operands = new HashMap<>();
        release.operands().forEach(value -> operands.computeIfAbsent(value.nodeId(), ignored -> new ArrayList<>()).add(value));
        Map<String, List<ReleaseRuleActionProjection>> ruleActions = new HashMap<>();
        release.ruleActions().forEach(value -> ruleActions.computeIfAbsent(value.ruleId(), ignored -> new ArrayList<>())
            .add(value));
        ArrayList<RuleProjection> projected = new ArrayList<>();
        for (ReleaseRuleProjection rule : release.rules()) {
            RuleProjection source = expectedRules.get(rule.ruleCode());
            List<ReleaseNodeProjection> ruleNodes = nodes.getOrDefault(rule.ruleId(), List.of());
            List<ReleaseNodeProjection> roots = ruleNodes.stream()
                .filter(value -> value.parentNodeId() == null).toList();
            List<ReleaseRuleActionProjection> bindings = ruleActions.getOrDefault(rule.ruleId(), List.of());
            if (source == null || roots.size() != 1 || bindings.size() != 1) return false;
            ReleaseActionVersionProjection action = actions.get(bindings.getFirst().actionVersionId());
            if (action == null) return false;
            projected.add(new RuleProjection(rule.ruleCode(), source.ruleName(), rule.priorityNo(), rule.enabledFlag(),
                rule.reasonCode(), releaseCondition(roots.getFirst(), ruleNodes, operands, fields,
                    new HashSet<>(), 1),
                new ActionProjection(action.actionCode(), action.versionNo(), action.handlerCode(),
                    action.implementationVersion(), action.implementationHash(), action.actionCategory(),
                    action.terminalFlag(), action.decisionCode(), action.mergePolicy(),
                    action.shadowSupportedFlag()), rule.reasonMessage(), List.of()));
        }
        RuleSetVersionProjection actual = new RuleSetVersionProjection(expected.pointCode(), expected.ruleSetCode(),
            expected.ruleSetName(), expected.versionNo(), projected, expected.contentFormat());
        RiskDecisionCanonicalizerImpl canonicalizer = new RiskDecisionCanonicalizerImpl();
        return canonicalizer.ruleSetVersion(expected).sha256LowerHex()
            .equals(canonicalizer.ruleSetVersion(actual).sha256LowerHex());
    }

    private static ConditionProjection releaseCondition(ReleaseNodeProjection node,
                                                        List<ReleaseNodeProjection> allNodes,
                                                        Map<String, List<ReleaseOperandProjection>> operands,
                                                        Map<String, ReleaseInputFieldProjection> fields,
                                                        Set<String> visiting,
                                                        int depth) {
        if (depth > 12 || !visiting.add(node.nodeId())) {
            throw new IllegalArgumentException("invalid release AST");
        }
        if (node.nodeType() == NodeType.GROUP) {
            List<ConditionProjection> children = allNodes.stream()
                .filter(value -> node.nodeId().equals(value.parentNodeId()))
                .sorted(Comparator.comparingInt(ReleaseNodeProjection::sequenceNo))
                .map(value -> releaseCondition(value, allNodes, operands, fields, visiting, depth + 1)).toList();
            visiting.remove(node.nodeId());
            return new GroupProjection(node.groupOperator(), children);
        }
        List<OperandProjection> values = operands.getOrDefault(node.nodeId(), List.of()).stream()
            .map(value -> new OperandProjection(value.operandRole(), value.valueSequence(), value.sourceType(),
                value.inputFieldId() == null ? null : fields.get(value.inputFieldId()).fieldCode(), value.valueType(),
                releaseLiteral(value))).toList();
        visiting.remove(node.nodeId());
        return new PredicateProjection(node.comparisonOperator(), values);
    }

    private static RiskDecisionCanonicalizer.LiteralProjection releaseLiteral(ReleaseOperandProjection value) {
        if (value.sourceType() != OperandSource.LITERAL) return NullValue.INSTANCE;
        if (value.booleanValue() != null) return new BooleanValue(value.booleanValue());
        String literal = value.stringValue() != null ? value.stringValue()
            : value.numberValue() != null ? value.numberValue()
            : value.datetimeValue() != null ? value.datetimeValue() : value.durationValue();
        return literal == null ? NullValue.INSTANCE : new StringValue(literal);
    }

    private static boolean inputMatches(CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor expected,
                                        RiskDecisionCanonicalizer.InputFieldProjection actual) {
        return expected.fieldCode().equals(actual.fieldCode()) && expected.valueType() == actual.valueType()
            && expected.requiredFlag() == actual.requiredFlag()
            && expected.dataClassification() == actual.dataClassification()
            && expected.ruleReferenceFlag() == actual.ruleReferenceFlag()
            && expected.actionParameterFlag() == actual.actionParameterFlag();
    }

    private static boolean inputMatches(CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor expected,
                                        ReleaseInputFieldProjection actual) {
        return expected.fieldCode().equals(actual.fieldCode()) && expected.valueType() == actual.valueType()
            && expected.requiredFlag() == actual.requiredFlag()
            && expected.dataClassification() == actual.dataClassification()
            && expected.ruleReferenceFlag() == actual.ruleReferenceFlag()
            && expected.actionParameterFlag() == actual.actionParameterFlag();
    }

    private static List<StaticViolation> astShape(DecisionReleaseProjection r) {
        ArrayList<StaticViolation> out = new ArrayList<>();
        if (r.rules().isEmpty() || r.rules().size() > 200) {
            add(out, "RULE_COUNT", "rules", null, null, null);
        }
        if (r.nodes().size() > 2000) add(out, "NODE_COUNT", "nodes", null, null, null);
        if (r.operands().size() > 10000) add(out, "OPERAND_COUNT", "operands", null, null, null);
        Set<String> ruleCodes = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        for (ReleaseRuleProjection rule : r.rules()) {
            if (rule.ruleCode() == null || !rule.ruleCode().matches("[A-Z][A-Z0-9_]{0,63}")
                || rule.priorityNo() <= 0 || !ruleCodes.add(rule.ruleCode())
                || !priorities.add(rule.priorityNo())) {
                add(out, "RULE_DIRECTORY", "rules", rule.ruleCode(), id(rule.ruleId()), null);
            }
        }
        for (ReleaseOperandProjection operand : r.operands()) {
            if (operand.operandRole() == null || operand.sourceType() == null || operand.valueType() == null
                || operand.valueSequence() <= 0) {
                add(out, "OPERAND_METADATA", "operands/" + operand.operandId(), null,
                    id(operand.nodeId()), null);
            }
        }
        Map<String, List<ReleaseNodeProjection>> byRule = new HashMap<>();
        r.nodes().forEach(v -> byRule.computeIfAbsent(v.ruleId(), ignored -> new ArrayList<>()).add(v));
        for (ReleaseRuleProjection rule : r.rules()) {
            List<ReleaseNodeProjection> nodes = byRule.getOrDefault(rule.ruleId(), List.of());
            List<ReleaseNodeProjection> roots = nodes.stream().filter(v -> v.parentNodeId() == null).toList();
            if (roots.size() != 1) add(out, "AST_ROOT", "rules/" + rule.ruleId(), rule.ruleCode(), null, null);
            else if (roots.getFirst().sequenceNo() != 1) add(out, "AST_ROOT_SEQUENCE", "rules/" + rule.ruleId(), rule.ruleCode(), id(roots.getFirst().nodeId()), null);
            Set<String> ids = nodes.stream().map(ReleaseNodeProjection::nodeId).collect(java.util.stream.Collectors.toSet());
            for (ReleaseNodeProjection node : nodes) {
                boolean badMatrix = node.nodeType() == null || node.sequenceNo() <= 0
                    || node.nodeType() == NodeType.GROUP && (node.groupOperator() == null || node.comparisonOperator() != null)
                    || node.nodeType() == NodeType.PREDICATE && (node.groupOperator() != null || node.comparisonOperator() == null);
                if (badMatrix) add(out, "AST_NODE_MATRIX", "nodes/" + node.nodeId(), rule.ruleCode(), id(node.nodeId()), null);
                if (node.parentNodeId() != null && !ids.contains(node.parentNodeId()))
                    add(out, "AST_PARENT", "nodes/" + node.nodeId(), rule.ruleCode(), id(node.nodeId()), null);
            }
            Set<String> visited = new HashSet<>();
            if (!roots.isEmpty()) walk(roots.getFirst(), nodes, new HashSet<>(), visited, 1, out, rule.ruleCode());
            if (visited.size() != nodes.size()) add(out, "AST_UNREACHABLE", "rules/" + rule.ruleId(), rule.ruleCode(), null, null);
            Map<String, List<ReleaseNodeProjection>> children = new HashMap<>();
            nodes.stream().filter(v -> v.parentNodeId() != null).forEach(v -> children.computeIfAbsent(v.parentNodeId(), x -> new ArrayList<>()).add(v));
            children.values().forEach(list -> {
                List<Integer> seq = list.stream().map(ReleaseNodeProjection::sequenceNo).sorted().toList();
                for (int i = 0; i < seq.size(); i++) if (seq.get(i) != i + 1) add(out, "AST_SEQUENCE", "nodes", rule.ruleCode(), null, null);
            });
            for (ReleaseNodeProjection group : nodes.stream().filter(v -> v.nodeType() == NodeType.GROUP).toList()) {
                int count = children.getOrDefault(group.nodeId(), List.of()).size();
                if (count == 0 || group.groupOperator() == GroupOperator.NOT && count != 1)
                    add(out, "AST_GROUP_CHILDREN", "nodes/" + group.nodeId(), rule.ruleCode(), id(group.nodeId()), null);
                if ((group.groupOperator() == GroupOperator.AND || group.groupOperator() == GroupOperator.OR) && count < 2)
                    add(out, "AST_GROUP_CHILDREN", "nodes/" + group.nodeId(), rule.ruleCode(), id(group.nodeId()), null);
            }
            for (ReleaseNodeProjection predicate : nodes.stream().filter(v -> v.nodeType() == NodeType.PREDICATE).toList()) {
                if (!children.getOrDefault(predicate.nodeId(), List.of()).isEmpty())
                    add(out, "AST_PREDICATE_CHILD", "nodes/" + predicate.nodeId(), rule.ruleCode(), id(predicate.nodeId()), null);
            }
        }
        return out;
    }

    private static void walk(ReleaseNodeProjection node, List<ReleaseNodeProjection> nodes, Set<String> visiting,
                             Set<String> visited, int depth, List<StaticViolation> out, String ruleCode) {
        if (depth > 12 || !visiting.add(node.nodeId())) { add(out, "AST_CYCLE_DEPTH", "nodes/" + node.nodeId(), ruleCode, id(node.nodeId()), null); return; }
        for (ReleaseNodeProjection child : nodes.stream().filter(v -> node.nodeId().equals(v.parentNodeId())).toList())
            walk(child, nodes, visiting, visited, depth + 1, out, ruleCode);
        visiting.remove(node.nodeId()); visited.add(node.nodeId());
    }

    private static List<StaticViolation> astTypes(DecisionReleaseProjection r) {
        ArrayList<StaticViolation> out = new ArrayList<>();
        Map<String, ReleaseInputFieldProjection> fields = index(r.inputFields(), ReleaseInputFieldProjection::inputFieldId);
        Map<String, List<ReleaseOperandProjection>> operands = new HashMap<>();
        r.operands().forEach(v -> operands.computeIfAbsent(v.nodeId(), ignored -> new ArrayList<>()).add(v));
        for (ReleaseNodeProjection node : r.nodes()) {
            List<ReleaseOperandProjection> values = operands.getOrDefault(node.nodeId(), List.of());
            if (node.nodeType() == NodeType.GROUP && !values.isEmpty()) add(out, "GROUP_OPERANDS", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
            if (node.nodeType() != NodeType.PREDICATE) continue;
            List<ReleaseOperandProjection> context = values.stream().filter(v -> v.sourceType() == OperandSource.CONTEXT).toList();
            List<ReleaseOperandProjection> literals = values.stream().filter(v -> v.sourceType() == OperandSource.LITERAL).toList();
            int expected = node.comparisonOperator() == ComparisonOperator.IS_NULL || node.comparisonOperator() == ComparisonOperator.IS_NOT_NULL ? 0
                : node.comparisonOperator() == ComparisonOperator.BETWEEN ? 2 : 1;
            if (node.comparisonOperator() == ComparisonOperator.IN || node.comparisonOperator() == ComparisonOperator.NOT_IN)
                expected = literals.size() >= 1 && literals.size() <= 100 ? literals.size() : -1;
            if (context.size() != 1 || literals.size() != expected || values.size() != context.size() + literals.size())
                add(out, "OPERAND_MATRIX", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
            if (context.size() == 1) {
                ReleaseOperandProjection source = context.getFirst();
                ReleaseInputFieldProjection sourceField = fields.get(source.inputFieldId());
                if (source.valueSequence() != 1 || source.inputFieldId() == null || source.featureVersionId() != null
                    || literalCount(source) != 0) add(out, "CONTEXT_MATRIX", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
                var opposite = source.operandRole() == com.paytech.risk.decision.domain.enums.OperandRole.LEFT
                    ? com.paytech.risk.decision.domain.enums.OperandRole.RIGHT
                    : com.paytech.risk.decision.domain.enums.OperandRole.LEFT;
                List<Integer> sequence = literals.stream().filter(v -> v.operandRole() == opposite)
                    .map(ReleaseOperandProjection::valueSequence).sorted().toList();
                if (sequence.size() != literals.size()) add(out, "OPERAND_ROLE", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
                for (int index = 0; index < sequence.size(); index++) if (sequence.get(index) != index + 1)
                    add(out, "OPERAND_SEQUENCE", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
                if (sourceField != null) {
                    for (ReleaseOperandProjection literal : literals) {
                        if (literal.valueType() != source.valueType()
                            || !canonicalLiteral(literal, sourceField.fieldCode())) {
                            add(out, "LITERAL_TYPE", "operands/" + literal.operandId(), null,
                                id(node.nodeId()), null);
                        }
                    }
                }
            }
            if (Set.of(ComparisonOperator.GT, ComparisonOperator.GTE, ComparisonOperator.LT,
                ComparisonOperator.LTE, ComparisonOperator.BETWEEN).contains(node.comparisonOperator())
                && context.size() == 1 && !Set.of(DecisionValueType.NUMBER, DecisionValueType.DATETIME,
                    DecisionValueType.DURATION).contains(context.getFirst().valueType())) {
                add(out, "ORDERED_TYPE", "nodes/" + node.nodeId(), null, id(node.nodeId()), null);
            }
            for (ReleaseOperandProjection value : values) {
                if (value.sourceType() == OperandSource.FEATURE) add(out, "FEATURE_OPERAND", "operands/" + value.operandId(), null, id(node.nodeId()), null);
                if (value.sourceType() == OperandSource.CONTEXT) {
                    ReleaseInputFieldProjection field = fields.get(value.inputFieldId());
                    if (field == null || field.valueType() != value.valueType() || !field.ruleReferenceFlag())
                        add(out, "CONTEXT_TYPE", "operands/" + value.operandId(), null, id(node.nodeId()), null);
                }
                if (value.sourceType() == OperandSource.LITERAL && (value.inputFieldId() != null
                    || value.featureVersionId() != null || literalCount(value) != 1 || !literalMatchesType(value)))
                    add(out, "LITERAL_TYPE", "operands/" + value.operandId(), null, id(node.nodeId()), null);
            }
        }
        return out;
    }

    private static boolean canonicalLiteral(ReleaseOperandProjection value, String fieldCode) {
        if (!literalMatchesType(value)) return false;
        return switch (value.valueType()) {
            case NUMBER -> canonicalNumber(value.numberValue());
            case DATETIME -> canonicalDatetime(value.datetimeValue());
            case DURATION -> canonicalDuration(value.durationValue());
            case STRING -> canonicalString(fieldCode, value.stringValue());
            case BOOLEAN -> true;
        };
    }

    private static boolean canonicalNumber(String raw) {
        try {
            if (raw == null || !raw.matches("-?(?:0|[1-9][0-9]{0,15})\\.[0-9]{8}")) return false;
            BigDecimal value = new BigDecimal(raw);
            return !(value.signum() == 0 && raw.startsWith("-")) && value.abs().compareTo(
                new BigDecimal("9999999999999999.99999999")) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean canonicalDatetime(String raw) {
        return CanonicalUtcMicrosecond.isCanonical(raw);
    }

    private static boolean canonicalDuration(String raw) {
        if (raw == null || !raw.matches("0|[1-9][0-9]*")) return false;
        try {
            return Long.parseLong(raw) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean canonicalString(String fieldCode, String raw) {
        if (raw == null || !Normalizer.isNormalized(raw, Normalizer.Form.NFC)) return false;
        return switch (fieldCode) {
            case "country_code" -> raw.matches("[A-Z]{2}");
            case "currency" -> raw.matches("[A-Z]{3}");
            case "mcc_code" -> raw.matches("[0-9]{4}");
            case "industry_code" -> raw.matches("[A-Z][A-Z0-9_]{0,63}");
            case "entity_source_type" -> Set.of("PLATFORM", "CLIENT", "PARTNER").contains(raw);
            case "locale" -> raw.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
                && Locale.forLanguageTag(raw).toLanguageTag().equals(raw);
            default -> true;
        };
    }

    private static int literalCount(ReleaseOperandProjection v) {
        int count = 0;
        if (v.stringValue() != null) count++;
        if (v.numberValue() != null) count++;
        if (v.booleanValue() != null) count++;
        if (v.datetimeValue() != null) count++;
        if (v.durationValue() != null) count++;
        return count;
    }

    private static boolean literalMatchesType(ReleaseOperandProjection v) {
        return switch (v.valueType()) {
            case STRING -> v.stringValue() != null;
            case NUMBER -> v.numberValue() != null;
            case BOOLEAN -> v.booleanValue() != null;
            case DATETIME -> v.datetimeValue() != null;
            case DURATION -> v.durationValue() != null;
        };
    }

    private static List<StaticViolation> handlerHash(DecisionReleaseClosure closure) {
        ArrayList<StaticViolation> out = new ArrayList<>();
        Map<String, PersistedActionVersion> persisted = new HashMap<>();
        Map<String, CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor> builtIn = index(
            CheckoutPrepareAuthoringDescriptor.v1().actions(), v -> v.actionCode() + ":" + v.versionNo());
        Set<Long> persistedIds = new HashSet<>();
        Set<String> persistedKeys = new HashSet<>();
        for (PersistedActionVersion row : closure.persistedActions()) {
            if (row == null || row.actionVersionId() <= 0 || !persistedIds.add(row.actionVersionId())
                || !persistedKeys.add(row.actionCode() + ":" + row.versionNo())) {
                add(out, "PERSISTED_ACTION_DUPLICATE", "persistedActions", null, null, row == null ? null : row.actionCode());
            } else {
                persisted.put(Long.toString(row.actionVersionId()), row);
                CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor expected = builtIn.get(
                    row.actionCode() + ":" + row.versionNo());
                if (expected == null || row.actionVersionId() != 240001L
                    + CheckoutPrepareAuthoringDescriptor.v1().actions().indexOf(expected)
                    || row.status() != ConfigurationStatus.ACTIVE
                    || !expected.handlerCode().equals(row.handlerCode())
                    || !expected.implementationVersion().equals(row.implementationVersion())
                    || !expected.implementationHash().equals(row.implementationHash())
                    || expected.actionCategory() != row.actionCategory()
                    || expected.terminalFlag() != row.terminalFlag()
                    || expected.decisionCode() != row.decisionCode()
                    || expected.mergePolicy() != row.mergePolicy()
                    || expected.shadowSupportedFlag() != row.shadowSupportedFlag()) {
                    add(out, "PERSISTED_ACTION_DRIFT", "persistedActions", null, null, row.actionCode());
                }
            }
        }
        for (ReleaseActionVersionProjection action : closure.release().actionVersions()) {
            PersistedActionVersion row = persisted.get(action.actionVersionId());
            CheckoutPrepareAuthoringDescriptor.DecisionActionDescriptor expected = builtIn.get(action.actionCode() + ":" + action.versionNo());
            boolean invalid = row == null || expected == null || row.status() != ConfigurationStatus.ACTIVE
                || !action.actionCode().equals(row.actionCode()) || action.versionNo() != row.versionNo()
                || !action.handlerCode().equals(row.handlerCode()) || !action.implementationVersion().equals(row.implementationVersion())
                || !action.implementationHash().equals(row.implementationHash())
                || action.actionCategory() != row.actionCategory()
                || action.terminalFlag() != row.terminalFlag()
                || action.decisionCode() != row.decisionCode()
                || action.mergePolicy() != row.mergePolicy()
                || action.shadowSupportedFlag() != row.shadowSupportedFlag()
                || !action.handlerCode().equals(expected.handlerCode())
                || !action.implementationVersion().equals(expected.implementationVersion())
                || !action.implementationHash().equals(expected.implementationHash())
                || action.actionCategory() != expected.actionCategory()
                || action.terminalFlag() != expected.terminalFlag()
                || action.decisionCode() != expected.decisionCode()
                || action.mergePolicy() != expected.mergePolicy()
                || action.shadowSupportedFlag() != expected.shadowSupportedFlag();
            if (invalid) add(out, "HANDLER_HASH_MISMATCH", "actionVersions/" + action.actionVersionId(), null, null, action.actionCode());
        }
        return out;
    }

    private static List<StaticViolation> terminalActions(DecisionReleaseProjection r) {
        ArrayList<StaticViolation> out = new ArrayList<>();
        Map<String, ReleaseActionVersionProjection> actions = index(r.actionVersions(), ReleaseActionVersionProjection::actionVersionId);
        Map<String, List<ReleaseRuleActionProjection>> byRule = new HashMap<>();
        r.ruleActions().forEach(v -> byRule.computeIfAbsent(v.ruleId(), ignored -> new ArrayList<>()).add(v));
        for (ReleaseRuleProjection rule : r.rules()) {
            List<ReleaseRuleActionProjection> bindings = byRule.getOrDefault(rule.ruleId(), List.of());
            if (bindings.size() != 1 || bindings.getFirst().sequenceNo() != 1) { add(out, "TERMINAL_ACTION_COUNT", "rules/" + rule.ruleId(), rule.ruleCode(), null, null); continue; }
            ReleaseActionVersionProjection action = actions.get(bindings.getFirst().actionVersionId());
            if (action == null || !action.terminalFlag() || action.actionCategory() != ActionCategory.DECISION
                || action.decisionCode() == null || action.mergePolicy() != com.paytech.risk.decision.domain.enums.MergePolicy.MOST_RESTRICTIVE
                || !action.shadowSupportedFlag())
                add(out, "TERMINAL_ACTION_REQUIRED", "rules/" + rule.ruleId(), rule.ruleCode(), null,
                    action == null ? null : action.actionCode());
        }
        return out;
    }

    private static List<StaticViolation> unsupported(String code, String path, boolean present) {
        return present ? List.of(new StaticViolation(code, path, null, null, null)) : List.of();
    }

    private static <T> Set<String> ids(List<T> rows, Function<T, String> getter, List<StaticViolation> out, String code) {
        Set<String> values = new HashSet<>();
        for (T row : rows) {
            String value = row == null ? null : getter.apply(row);
            if (!validId(value)) add(out, "INVALID_ID", "ids", null, null, null);
            else if (!values.add(value)) add(out, code, "duplicates", null, null, null);
        }
        return values;
    }

    private static boolean validId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) return false;
        try { return Long.parseLong(value) > 0; } catch (NumberFormatException exception) { return false; }
    }

    private static <T> Map<String, T> index(List<T> rows, Function<T, String> getter) {
        Map<String, T> out = new HashMap<>(); for (T row : rows) out.putIfAbsent(getter.apply(row), row); return out;
    }

    private static void add(List<StaticViolation> values, String code, String path, String rule, Long node, String action) {
        values.add(new StaticViolation(code, path, rule, node, action));
    }

    private static Long id(String value) { try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException e) { return null; } }

    private static StaticViolation normalize(StaticViolation value) {
        return new StaticViolation(nfc(value.errorCode()), nfc(value.path()), nfc(value.ruleCode()), value.nodeId(), nfc(value.actionCode()));
    }

    private static String nfc(String value) { return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC); }

    private static final Comparator<String> UNSIGNED_UTF8 = (left, right) -> {
        byte[] a = left.getBytes(StandardCharsets.UTF_8); byte[] b = right.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int compared = Integer.compare(Byte.toUnsignedInt(a[i]), Byte.toUnsignedInt(b[i])); if (compared != 0) return compared;
        }
        return Integer.compare(a.length, b.length);
    };
    private static final Comparator<String> NULL_LAST_STRING = Comparator.nullsLast(UNSIGNED_UTF8);
    private static final Comparator<Long> NULL_LAST_LONG = Comparator.nullsLast(Long::compareTo);
    private static final Comparator<StaticViolation> VIOLATION_ORDER = Comparator
        .comparing(StaticViolation::errorCode, NULL_LAST_STRING)
        .thenComparing(StaticViolation::path, NULL_LAST_STRING)
        .thenComparing(StaticViolation::ruleCode, NULL_LAST_STRING)
        .thenComparing(StaticViolation::nodeId, NULL_LAST_LONG)
        .thenComparing(StaticViolation::actionCode, NULL_LAST_STRING);
}
