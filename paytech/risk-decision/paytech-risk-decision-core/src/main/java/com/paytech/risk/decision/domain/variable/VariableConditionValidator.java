package com.paytech.risk.decision.domain.variable;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 规则 AST 与流程条件的共同纯校验器，所有业务变量和成员由当前库快照传入。 */
public final class VariableConditionValidator {
    /** 该校验器不读取数据库，也不执行条件。 */
    private VariableConditionValidator() { }

    /** 保存时使用的已锁定当前定义、成员及准确用途。 */
    public record Context(String pointCode, VariableUsage usage, String nodeType,
        Map<String, Entry> variables, Map<String, List<Member>> collections) { }

    /** 按共同操作数矩阵规范化左右两侧，不产生任何运行判断结果。 */
    public static List<VariableOperand> validate(ComparisonOperator operator, List<VariableOperand> operands, Context context) {
        if (operator == null || operands == null || operands.isEmpty() || operands.size() > 101) { throw invalid("条件操作数数量无效"); }
        if (operands.stream().anyMatch(o -> o == null || o.operandRole() == null)) { throw invalid("操作数角色必填"); }
        List<VariableOperand> ordered = operands.stream().sorted(Comparator.comparing(VariableOperand::operandRole)
            .thenComparingInt(VariableOperand::valueSequence)).toList();
        List<Resolved> left = resolveRole(ordered, OperandRole.LEFT, context);
        List<Resolved> right = resolveRole(ordered, OperandRole.RIGHT, context);
        if (left.size() != 1 || left.getFirst().collection()) { throw invalid("左侧必须是一个标量"); }
        validateShape(operator, left.getFirst(), right);
        for (Resolved operand : right) { compatible(left.getFirst(), operand, context); }
        if (operator == ComparisonOperator.BETWEEN) { bounds(right); }
        List<VariableOperand> normalized = new ArrayList<>();
        Resolved first = left.getFirst();
        Definition rightDomain = right.size() == 1 ? right.getFirst().definition() : null;
        normalized.add(normalizeLiteral(first, rightDomain));
        right.forEach(value -> normalized.add(normalizeLiteral(value, first.definition())));
        return List.copyOf(normalized);
    }

    /** 变量格式会规范化对侧字面量；共享变量值不会因此被写回或内联。 */
    static VariableOperand normalizeLiteral(Resolved value, Definition peer) {
        VariableOperand operand = value.operand();
        if (operand.sourceType() != OperandSource.LITERAL || peer == null) return operand;
        Object normalized = VariableValueNormalizer.normalize(peer.valueType(), peer.formatCode(), value.knownValues().getFirst());
        return new VariableOperand(operand.operandRole(), operand.valueSequence(), operand.sourceType(), operand.variableCode(),
            operand.fieldCode(), operand.valueType(), literal(normalized), operand.numericUnit(), operand.currencyCode(), operand.currencyVariableCode());
    }

    /** 同侧序号必须从一开始连续；禁止空节点或错误角色绕过计数。 */
    static List<Resolved> resolveRole(List<VariableOperand> operands, OperandRole role, Context context) {
        if (operands.stream().anyMatch(o -> o == null || o.operandRole() == null)) { throw invalid("操作数角色必填"); }
        List<VariableOperand> selected = operands.stream().filter(o -> o.operandRole() == role).toList();
        for (int index = 0; index < selected.size(); index++) {
            if (selected.get(index).valueSequence() != index + 1) { throw invalid("操作数序号必须连续"); }
        }
        return selected.stream().map(o -> resolve(o, context)).toList();
    }

    /** 区分一元、比较、区间与集合，避免把集合展开成内联常量。 */
    static void validateShape(ComparisonOperator operator, Resolved left, List<Resolved> right) {
        if (operator == ComparisonOperator.IS_NULL || operator == ComparisonOperator.IS_NOT_NULL) {
            if (!right.isEmpty()) { throw invalid("空值判断不能填写右侧"); }
            return;
        }
        if (operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN) {
            boolean collection = right.size() == 1 && right.getFirst().collection();
            boolean literals = !right.isEmpty() && right.size() <= 100 && right.stream().allMatch(r -> r.operand().sourceType() == OperandSource.LITERAL);
            if (!collection && !literals) { throw invalid("集合包含须选择一个集合变量或 1–100 个字面量"); }
            return;
        }
        int count = operator == ComparisonOperator.BETWEEN ? 2 : 1;
        if (right.size() != count || right.stream().anyMatch(Resolved::collection)) { throw invalid("比较操作数数量或集合类型无效"); }
        if (operator != ComparisonOperator.EQ && operator != ComparisonOperator.NE
            && !Set.of(DecisionValueType.NUMBER, DecisionValueType.DATETIME, DecisionValueType.DURATION).contains(left.type())) {
            throw invalid("大小和区间判断只支持 NUMBER、DATETIME、DURATION");
        }
    }

    /** 来源解析只查传入快照；新格式拒绝旧 CONTEXT 与未规范化附加字段。 */
    static Resolved resolve(VariableOperand operand, Context context) {
        if (operand == null || operand.valueType() == null || operand.fieldCode() != null) { throw invalid("操作数类型或字段结构错误"); }
        if (operand.sourceType() == OperandSource.VARIABLE) { return variable(operand, context); }
        if (operand.sourceType() != OperandSource.LITERAL || operand.variableCode() != null) { throw invalid("仅允许 VARIABLE 或 LITERAL 来源"); }
        Object value = value(operand.literalValue());
        VariableFormat format = operand.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE;
        value = VariableValueNormalizer.normalize(operand.valueType(), format, value);
        Definition d = literalDefinition(operand, context.pointCode());
        VariableDefinitionValidator.validate(d);
        VariableDefinitionValidator.dependencies(d, context.variables());
        VariableOperand normalized = new VariableOperand(operand.operandRole(), operand.valueSequence(), OperandSource.LITERAL,
            null, null, operand.valueType(), literal(value), operand.numericUnit(), operand.currencyCode(), operand.currencyVariableCode());
        return new Resolved(normalized, null, operand.valueType(), operand.numericUnit(), currency(d, context), false, List.of(value));
    }

    /** 变量声明类型必须与库一致，来源输出只能供当前规则集节点的出线使用。 */
    static Resolved variable(VariableOperand operand, Context context) {
        if (!(operand.literalValue() instanceof NullLiteral) || operand.numericUnit() != null
            || operand.currencyCode() != null || operand.currencyVariableCode() != null) { throw invalid("变量引用不能附带值或金额元数据"); }
        VariableDefinitionValidator.code(operand.variableCode());
        Entry entry = context.variables().get(operand.variableCode());
        if (entry == null) { throw fail(VARIABLE_NOT_FOUND, "变量不存在：" + operand.variableCode()); }
        Definition d = entry.definition();
        if (entry.status() != VariableStatus.ACTIVE) { throw fail(VARIABLE_DISABLED, "变量未启用：" + d.variableCode()); }
        if (operand.valueType() != d.valueType()) { throw fail(VARIABLE_TYPE_MISMATCH, "声明类型与变量库不一致"); }
        if (!d.usages().contains(context.usage()) || !VariableDefinitionValidator.covers(d.pointCode(), context.pointCode())
            || d.sourceType() == VariableSource.NODE_OUTPUT && (context.usage() != VariableUsage.FLOW_CONDITION || !"RULE_SET".equals(context.nodeType()))) {
            throw fail(VARIABLE_SCOPE_MISMATCH, "变量不适用于当前条件位置");
        }
        VariableDefinitionValidator.dependencies(d, context.variables());
        boolean collection = d.sourceType() == VariableSource.COLLECTION;
        List<Object> known = collection ? requiredMembers(d.variableCode(), context).stream().map(Member::value).toList()
            : d.sourceType() == VariableSource.CONSTANT ? List.of(entry.currentValue()) : null;
        domain(d, known, context);
        return new Resolved(operand, d, d.valueType(), d.numericUnit(), currency(d, context), collection, known);
    }

    /** 字面量有显式金额元数据，临时定义只复用通用结构校验而不写库。 */
    static Definition literalDefinition(VariableOperand operand, String pointCode) {
        return new Definition("LITERAL", VariableSource.CONSTANT, operand.valueType(), pointCode, null, null, null,
            Set.of(VariableUsage.RULE_CONDITION), false, operand.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE,
            operand.numericUnit(), operand.currencyCode(), operand.currencyVariableCode(), null);
    }

    /** 数值要求相同量纲和准确币种绑定；已知值验证两侧声明的格式/可选值。 */
    static void compatible(Resolved left, Resolved right, Context context) {
        if (left.type() != right.type()) { throw fail(VARIABLE_TYPE_MISMATCH, "左右两侧值类型不同"); }
        if (left.unit() != right.unit() || !Objects.equals(left.currency(), right.currency())) { throw fail(VARIABLE_UNIT_MISMATCH, "左右两侧量纲或币种来源不同"); }
        domain(left.definition(), right.knownValues(), context);
        domain(right.definition(), left.knownValues(), context);
    }

    /** 未知输入值不伪造；常量和集合当前值必须通过对侧格式与可选集合验证。 */
    static void domain(Definition definition, List<Object> values, Context context) {
        if (definition == null || values == null) { return; }
        Set<String> allowed = definition.allowedValuesVariableCode() == null ? null
            : requiredMembers(definition.allowedValuesVariableCode(), context).stream()
                .map(m -> VariableValueNormalizer.key(definition.valueType(), m.value())).collect(java.util.stream.Collectors.toSet());
        for (Object value : values) {
            Object normalized = VariableValueNormalizer.normalize(definition.valueType(), definition.formatCode(), value);
            if (allowed != null && !allowed.contains(VariableValueNormalizer.key(definition.valueType(), normalized))) {
                throw fail(VARIABLE_DOMAIN_MISMATCH, "比较值不属于变量关联的可选集合");
            }
        }
    }

    /** 同源币种别名归一到准确协议坐标，固定币种不会自动等同于动态币种。 */
    static String currency(Definition definition, Context context) {
        if (definition.numericUnit() != NumericUnit.MONEY) { return null; }
        if (definition.currencyCode() != null) { return "FIXED:" + definition.currencyCode(); }
        Entry entry = context.variables().get(definition.currencyVariableCode());
        if (entry == null) { throw fail(VARIABLE_NOT_FOUND, "币种变量不存在"); }
        Definition d = entry.definition();
        return d.pointCode() + ":" + d.sourceContractCode() + ":" + d.sourceContractVersion() + ":" + d.sourceField();
    }

    /** 已知区间下界大于上界时拒绝保存，动态运行值不在本期求值。 */
    static void bounds(List<Resolved> operands) {
        var lower = operands.getFirst(); var upper = operands.getLast();
        if (lower.knownValues() == null || upper.knownValues() == null) { return; }
        String a = lower.knownValues().getFirst().toString(), b = upper.knownValues().getFirst().toString();
        int compared = switch (lower.type()) {
            case NUMBER -> new BigDecimal(a).compareTo(new BigDecimal(b));
            case DATETIME -> Instant.parse(a).compareTo(Instant.parse(b));
            case DURATION -> Long.compare(Long.parseLong(a), Long.parseLong(b));
            default -> throw invalid("区间类型无效");
        };
        if (compared > 0) { throw invalid("区间下界不能大于上界"); }
    }

    /** 成员必须由当前已锁定目录显式提供，缺失不当作空集合。 */
    static List<Member> requiredMembers(String code, Context context) {
        List<Member> members = context.collections().get(code);
        if (members == null) { throw fail(VARIABLE_NOT_FOUND, "集合当前成员尚未读取：" + code); }
        return members;
    }

    /** 旧 LiteralValue 类型仅作为安全标量容器复用，不允许 literal null。 */
    public static Object value(LiteralValue value) {
        return switch (value) {
            case StringLiteral s -> s.value();
            case BooleanLiteral b -> b.value();
            case null, default -> throw invalid("字面量不能为空");
        };
    }

    /** 将规范 JSON 标量转换为共享的领域容器。 */
    public static LiteralValue literal(Object value) {
        if (value instanceof Boolean b) { return new BooleanLiteral(b); }
        if (value instanceof String s) { return new StringLiteral(s); }
        throw invalid("字面量只接受规范字符串或 Boolean");
    }

    /** 仅在静态检查中携带当前已知值；不会进入保存 Hash。 */
    record Resolved(VariableOperand operand, Definition definition, DecisionValueType type, NumericUnit unit,
        String currency, boolean collection, List<Object> knownValues) { }
    /** 无效操作数需要调用方保留草稿并定位当前条件。 */
    static RiskVariableException invalid(String message) { return fail(INVALID_OPERAND, message); }
    /** 类型、单位和作用域失败供页面分别处理。 */
    static RiskVariableException fail(RiskVariableException.Reason reason, String message) { return new RiskVariableException(reason, message); }
}
