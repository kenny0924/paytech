package com.paytech.risk.decision.domain.rule;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import java.math.BigDecimal;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** CHECKOUT_PREPARE V1 的完整、纯内存 AST 验证。 */
public final class RuleAstValidatorImpl implements RuleAstValidator {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NODE_KEY = Pattern.compile("[\\x21-\\x7e]{1,64}");
    private static final Set<DecisionValueType> ORDERED = Set.of(
        DecisionValueType.NUMBER, DecisionValueType.DATETIME, DecisionValueType.DURATION);

    private final CheckoutPrepareAuthoringDescriptor descriptor;
    private final DecisionActionRegistry registry;
    private final Map<String, InputFieldDescriptor> fields;

    public RuleAstValidatorImpl(CheckoutPrepareAuthoringDescriptor descriptor, DecisionActionRegistry registry) {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.registry = Objects.requireNonNull(registry);
        Map<String, InputFieldDescriptor> index = new HashMap<>();
        descriptor.fields().forEach(field -> index.put(field.fieldCode(), field));
        fields = Map.copyOf(index);
    }

    @Override
    public NormalizedRuleSet validateAndNormalize(RuleSetDraft draft) {
        if (draft == null || !descriptor.point().pointCode().equals(draft.pointCode())
            || !validCode(draft.ruleSetCode()) || draft.rules() == null || draft.rules().isEmpty()
            || draft.rules().size() > 200
            || (draft.baseRuleSetVersionId() == null) != (draft.baseContentHash() == null)
            || (draft.baseRuleSetVersionId() != null && (draft.baseRuleSetVersionId() <= 0
                || !HASH.matcher(draft.baseContentHash()).matches()))) {
            throw invalid("规则集基础字段无效");
        }
        boolean variableFormat = "VARIABLE_REFERENCE".equals(draft.operandFormat());
        if (draft.operandFormat() != null && !variableFormat) { throw invalid("操作数格式不支持"); }
        Set<String> codes = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        long nodes = 0;
        long operands = 0;
        ArrayList<NormalizedRule> normalized = new ArrayList<>();
        for (RuleDraft rule : draft.rules()) {
            if (rule == null || !validCode(rule.ruleCode()) || rule.priorityNo() <= 0 || rule.conditionNodes() == null
                || rule.conditionNodes().isEmpty()) {
                throw invalid("规则目录无效");
            }
            if (!codes.add(rule.ruleCode()) || !priorities.add(rule.priorityNo())) throw business("规则目录重复");
            nodes += rule.conditionNodes().size();
            for (NodeDraft node : rule.conditionNodes()) {
                if (node == null || node.operands() == null) throw invalid("AST 节点无效");
                operands += node.operands().size();
            }
            if (nodes > 2000 || operands > 10000) throw invalid("规则 AST 超出限制");
            try {
                if (rule.action() != null) registry.require(rule.action());
            } catch (InvalidParameterException exception) {
                throw business("决策动作未注册");
            }
            List<AdditionalAction> additional = RuleActionConfigurationCatalog.normalize(rule.additionalActions());
            if (rule.action() == null && additional == null)
                throw business("旧格式规则必须包含终态决定");
            if (additional == null && rule.reasonMessage() != null) throw invalid("旧请求不能携带新增文案");
            String message = rule.reasonMessage() == null ? null : ChangeReasonPolicy.normalize(rule.reasonMessage());
            if (message != null && (rule.action() == null || !"DENY_CHECKOUT".equals(rule.action().actionCode())))
                throw business("拒绝文案只适用于拒绝规则");
            if (variableFormat && (additional == null || additional.stream().anyMatch(a -> a.subjectVariableCode() == null))) {
                throw invalid("统一变量配置必须提供附加动作数组及显式主体变量");
            }
            if (!variableFormat && additional != null && additional.stream().anyMatch(a -> a.subjectVariableCode() != null)) {
                throw invalid("旧格式不能混入主体变量");
            }
            NormalizedCondition condition = normalizeTree(rule.conditionNodes(), variableFormat);
            normalized.add(new NormalizedRule(rule.ruleCode(), text(rule.ruleName(), 255), rule.priorityNo(), rule.enabled(),
                optionalCode(rule.reasonCode()), condition, rule.action(), message, additional));
        }
        if (normalized.stream().anyMatch(rule -> rule.additionalActions() == null)
            && normalized.stream().anyMatch(rule -> rule.additionalActions() != null))
            throw invalid("不能混合旧请求和新增配置字段");
        normalized.sort(Comparator.comparingInt(NormalizedRule::priorityNo).thenComparing(NormalizedRule::ruleCode));
        return new NormalizedRuleSet(draft.pointCode(), draft.ruleSetCode(), text(draft.ruleSetName(), 255),
            draft.baseRuleSetVersionId(), draft.baseContentHash(), ChangeReasonPolicy.normalize(draft.changeReason()),
            normalized, draft.operandFormat());
    }

    @Override
    public NormalizedCondition validateCondition(List<NodeDraft> nodes) {
        return normalizeTree(nodes, false);
    }

    private NormalizedCondition normalizeTree(List<NodeDraft> nodes, boolean variableFormat) {
        Map<String, NodeDraft> byKey = new HashMap<>();
        Map<String, List<NodeDraft>> children = new HashMap<>();
        ArrayList<NodeDraft> roots = new ArrayList<>();
        for (NodeDraft node : nodes) {
            if (node == null || node.nodeKey() == null || !NODE_KEY.matcher(node.nodeKey()).matches()
                || node.parentNodeKey() != null && !NODE_KEY.matcher(node.parentNodeKey()).matches()
                || node.sequenceNo() <= 0) throw invalid("AST 节点无效");
            if (byKey.put(node.nodeKey(), node) != null) throw business("AST 节点重复");
            if (node.parentNodeKey() == null) roots.add(node);
            else children.computeIfAbsent(node.parentNodeKey(), ignored -> new ArrayList<>()).add(node);
        }
        if (roots.size() != 1 || roots.getFirst().sequenceNo() != 1) throw business("AST 必须只有一个根");
        for (NodeDraft node : nodes) {
            if (node.parentNodeKey() != null && !byKey.containsKey(node.parentNodeKey())) throw business("AST 存在孤儿节点");
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        NormalizedCondition root = visit(roots.getFirst(), children, visiting, visited, 1, variableFormat);
        if (visited.size() != nodes.size()) throw business("AST 含不可达节点");
        return root;
    }

    private NormalizedCondition visit(NodeDraft node, Map<String, List<NodeDraft>> childIndex,
                                      Set<String> visiting, Set<String> visited, int depth, boolean variableFormat) {
        if (depth > 12) throw invalid("AST 深度超出限制");
        if (!visiting.add(node.nodeKey())) throw business("AST 含环");
        List<NodeDraft> children = new ArrayList<>(childIndex.getOrDefault(node.nodeKey(), List.of()));
        children.sort(Comparator.comparingInt(NodeDraft::sequenceNo));
        for (int i = 0; i < children.size(); i++) if (children.get(i).sequenceNo() != i + 1) throw business("兄弟顺序不连续");
        NormalizedCondition result;
        if (node.nodeType() == NodeType.GROUP) {
            if (node.groupOperator() == null || node.comparisonOperator() != null || !node.operands().isEmpty()
                || children.isEmpty() || (node.groupOperator() == GroupOperator.NOT && children.size() != 1)
                || (node.groupOperator() == GroupOperator.AND || node.groupOperator() == GroupOperator.OR)
                    && children.size() < 2) {
                throw business("GROUP 节点矩阵无效");
            }
            result = new NormalizedGroup(node.groupOperator(), children.stream()
                .map(child -> visit(child, childIndex, visiting, visited, depth + 1, variableFormat)).toList());
        } else if (node.nodeType() == NodeType.PREDICATE) {
            if (node.groupOperator() != null || node.comparisonOperator() == null || !children.isEmpty())
                throw business("PREDICATE 节点矩阵无效");
            result = variableFormat ? variablePredicate(node) : predicate(node);
        } else throw business("节点类型无效");
        visiting.remove(node.nodeKey());
        visited.add(node.nodeKey());
        return result;
    }

    /** 首次准备只规范化请求自身，目录资格延后到幂等首次写入分支，保护成功回放。 */
    private NormalizedPredicate variablePredicate(NodeDraft node) {
        List<NormalizedOperand> values = new ArrayList<>();
        for (OperandDraft draft : node.operands()) {
            if (draft == null || draft.valueType() == null || draft.operandRole() == null || draft.fieldCode() != null) {
                throw invalid("统一操作数基础字段无效");
            }
            var operand = draft.variableOperand();
            VariableOperandJson.validateShape(operand);
            if (operand.sourceType() == OperandSource.VARIABLE) {
                VariableDefinitionValidator.code(operand.variableCode());
                if (operand.literalValue() != NullLiteral.INSTANCE || operand.numericUnit() != null
                    || operand.currencyCode() != null || operand.currencyVariableCode() != null) { throw invalid("变量不能附带值或量纲"); }
            } else if (operand.sourceType() != OperandSource.LITERAL || operand.variableCode() != null) {
                throw invalid("新格式仅支持 VARIABLE/LITERAL");
            }
            LiteralValue literal = operand.sourceType() == OperandSource.VARIABLE ? NullLiteral.INSTANCE
                : VariableConditionValidator.literal(VariableValueNormalizer.normalize(operand.valueType(),
                    operand.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE,
                    VariableConditionValidator.value(operand.literalValue())));
            values.add(new NormalizedOperand(operand.operandRole(), operand.valueSequence(), operand.sourceType(), null,
                operand.valueType(), literal, operand.variableCode(), operand.numericUnit(), operand.currencyCode(), operand.currencyVariableCode()));
        }
        values.sort(Comparator.comparing(NormalizedOperand::operandRole).thenComparingInt(NormalizedOperand::valueSequence));
        validateSequences(values);
        VariableOperandJson.validatePredicate(node.comparisonOperator(), values.stream().map(NormalizedOperand::variableOperand).toList());
        return new NormalizedPredicate(node.comparisonOperator(), values);
    }

    private NormalizedPredicate predicate(NodeDraft node) {
        ComparisonOperator operator = node.comparisonOperator();
        if (!descriptor.operators().contains(operator)) throw business("操作符无效");
        List<OperandDraft> operands = new ArrayList<>(node.operands());
        if (operands.stream().anyMatch(v -> v != null && (v.variableCode() != null || v.numericUnit() != null
            || v.currencyCode() != null || v.currencyVariableCode() != null || v.sourceType() == OperandSource.VARIABLE)))
            throw invalid("旧操作数不接受变量元数据");
        if (operands.stream().anyMatch(value -> value == null || value.operandRole() == null
            || value.sourceType() == null || value.valueType() == null)) throw invalid("操作数无效");
        operands.sort(Comparator.comparing((OperandDraft v) -> v.operandRole().ordinal())
            .thenComparingInt(OperandDraft::valueSequence));
        List<OperandDraft> context = operands.stream().filter(v -> v.sourceType() == OperandSource.CONTEXT).toList();
        List<OperandDraft> literals = operands.stream().filter(v -> v.sourceType() == OperandSource.LITERAL).toList();
        if (operands.stream().anyMatch(v -> v.sourceType() == OperandSource.FEATURE) || context.size() != 1)
            throw business("操作数来源无效");
        OperandDraft source = context.getFirst();
        InputFieldDescriptor field = fields.get(source.fieldCode());
        if (field == null || source.valueSequence() != 1
            || !field.ruleReferenceFlag() || source.valueType() != field.valueType()
            || source.literalValue() != NullLiteral.INSTANCE) throw business("上下文操作数无效");
        OperandRole literalRole = source.operandRole() == OperandRole.LEFT ? OperandRole.RIGHT : OperandRole.LEFT;
        int required;
        if (operator == ComparisonOperator.IS_NULL || operator == ComparisonOperator.IS_NOT_NULL) required = 0;
        else if (operator == ComparisonOperator.BETWEEN) required = 2;
        else if (operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN) {
            required = literals.size(); if (required < 1 || required > 100) throw invalid("IN 操作数数量无效");
        } else required = 1;
        if (literals.size() != required || operands.size() != required + 1) throw business("操作数矩阵无效");
        if (ORDERED.contains(field.valueType()) == false && Set.of(ComparisonOperator.GT, ComparisonOperator.GTE,
            ComparisonOperator.LT, ComparisonOperator.LTE, ComparisonOperator.BETWEEN).contains(operator)) {
            throw business("字段类型不支持有序比较");
        }
        ArrayList<NormalizedOperand> result = new ArrayList<>();
        for (OperandDraft operand : operands) {
            if (operand.operandRole() == null || operand.valueSequence() <= 0 || operand.valueType() != field.valueType())
                throw business("操作数类型无效");
            if (operand.sourceType() == OperandSource.LITERAL && (operand.fieldCode() != null
                || operand.operandRole() != literalRole || operand.literalValue() == null
                || operand.literalValue() == NullLiteral.INSTANCE)) {
                throw business("Literal 操作数无效");
            }
            LiteralValue literal = normalizeLiteral(operand, field.fieldCode());
            result.add(new NormalizedOperand(operand.operandRole(), operand.valueSequence(), operand.sourceType(),
                operand.fieldCode(), operand.valueType(), literal));
        }
        validateSequences(result);
        return new NormalizedPredicate(operator, result);
    }

    private static void validateSequences(List<NormalizedOperand> operands) {
        for (OperandRole role : OperandRole.values()) {
            List<Integer> values = operands.stream().filter(v -> v.operandRole() == role)
                .map(NormalizedOperand::valueSequence).sorted().toList();
            if (values.isEmpty()) continue;
            int start = values.getFirst();
            if (start != 1) throw business("操作数序号无效");
            for (int i = 1; i < values.size(); i++) if (values.get(i) != values.get(i - 1) + 1) throw business("操作数序号无效");
        }
    }

    private LiteralValue normalizeLiteral(OperandDraft operand, String fieldCode) {
        if (operand.sourceType() != OperandSource.LITERAL) return NullLiteral.INSTANCE;
        if (operand.valueType() == DecisionValueType.BOOLEAN) {
            if (!(operand.literalValue() instanceof BooleanLiteral)) throw business("布尔 Literal 类型无效");
            return operand.literalValue();
        }
        if (!(operand.literalValue() instanceof StringLiteral text)) throw business("字符串 Literal 类型无效");
        String value = text.value();
        if (value == null || value.length() > 512) throw invalid("Literal 长度无效");
        String normalized;
        try {
            normalized = switch (operand.valueType()) {
                case NUMBER -> normalizeDecimal(value);
                case DATETIME -> normalizeUtcMicrosecond(value);
                case DURATION -> normalizeDurationMillis(value);
                case STRING -> normalizeStringDomain(fieldCode, value);
                case BOOLEAN -> throw business("Literal 类型无效");
            };
        } catch (InvalidParameterException exception) {
            throw business("Literal 值无效");
        }
        return new StringLiteral(normalized);
    }

    private static String normalizeStringDomain(String field, String raw) {
        String value = nfc(raw);
        boolean valid = switch (field) {
            case "country_code" -> value.matches("[A-Z]{2}");
            case "currency" -> value.matches("[A-Z]{3}");
            case "mcc_code" -> value.matches("[0-9]{4}");
            case "industry_code" -> value.matches("[A-Z][A-Z0-9_]{0,63}");
            case "entity_source_type" -> Set.of("PLATFORM", "CLIENT", "PARTNER").contains(value);
            case "locale" -> validLocale(value);
            default -> true;
        };
        if (!valid) throw business("字段 Literal 值域无效");
        return field.equals("locale") ? Locale.forLanguageTag(value).toLanguageTag() : value;
    }

    private static boolean validLocale(String value) {
        if (!value.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) return false;
        Locale locale = Locale.forLanguageTag(value);
        if (locale.getLanguage().isBlank() || locale.toLanguageTag().equals("und")) return false;
        String canonical = locale.toLanguageTag();
        return !canonical.contains("und") && Locale.forLanguageTag(canonical).equals(locale);
    }

    @Override
    public String normalizeDecimal(String raw) {
        try {
            if (raw == null || !raw.matches("-?(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,8})?"))
                throw invalid("数值格式无效");
            BigDecimal value = new BigDecimal(raw).setScale(8);
            if (value.signum() == 0 && raw.startsWith("-") || value.abs().compareTo(
                new BigDecimal("9999999999999999.99999999")) > 0) throw invalid("数值超出范围");
            return value.toPlainString();
        } catch (NumberFormatException | ArithmeticException ex) { throw invalid("数值格式无效"); }
    }

    @Override
    public String normalizeUtcMicrosecond(String raw) {
        return CanonicalUtcMicrosecond.normalize(raw);
    }

    @Override
    public String normalizeDurationMillis(String raw) {
        if (raw == null || !raw.matches("0|[1-9][0-9]*")) throw invalid("时长格式无效");
        try { Long.parseLong(raw); return raw; } catch (NumberFormatException ex) { throw invalid("时长格式无效"); }
    }

    private static boolean validCode(String code) { return code != null && CODE.matcher(code).matches(); }
    private static String optionalCode(String code) { if (code == null) return null; if (!validCode(code)) throw invalid("原因代码无效"); return code; }
    private static String text(String value, int maxLength) {
        if (value == null || nfc(value).strip().isEmpty() || nfc(value).strip().length() > maxLength)
            throw invalid("名称或原因无效");
        return nfc(value).strip();
    }
    private static String nfc(String value) { return Normalizer.normalize(value, Normalizer.Form.NFC); }
    private static BizCheckFailException business(String message) { return new BizCheckFailException(message); }
    private static InvalidParameterException invalid(String message) { return new InvalidParameterException(message); }
}
