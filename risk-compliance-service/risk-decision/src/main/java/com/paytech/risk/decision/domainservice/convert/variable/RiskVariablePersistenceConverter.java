package com.paytech.risk.decision.domainservice.convert.variable;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import com.paytech.risk.decision.domain.variable.VariableValueNormalizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.EnumSet;
import java.util.stream.Stream;

/** decision 模块内的类型化行映射，不向跨模块合同泄露数据库对象。 */
public final class RiskVariablePersistenceConverter {
    /** 无状态转换器不允许实例化。 */
    private RiskVariablePersistenceConverter() { }

    /** 校验类型化列的唯一非空槽位，输入与节点输出不能携带运行实际值。 */
    public static Entry entry(RiskVariableDefinition row) {
        var usages = EnumSet.noneOf(VariableUsage.class);
        if (row.ruleConditionFlag()) { usages.add(VariableUsage.RULE_CONDITION); }
        if (row.flowConditionFlag()) { usages.add(VariableUsage.FLOW_CONDITION); }
        if (row.actionParameterFlag()) { usages.add(VariableUsage.ACTION_PARAMETER); }
        Definition definition = new Definition(row.variableCode(), row.sourceType(), row.valueType(), row.pointCode(),
            row.sourceContractCode(), row.sourceContractVersion(), row.sourceField(), usages, row.systemManagedFlag(),
            row.formatCode(), row.numericUnit(), row.currencyCode(), row.currencyVariableCode(), row.allowedValuesVariableCode());
        Object value = value(row.valueType(), row.stringValue(), row.numberValue(), row.booleanValue(), row.datetimeValue(),
            row.durationValue(), row.sourceType() == VariableSource.CONSTANT);
        return new Entry(definition, row.variableName(), row.description(), value, row.status(), row.version(),
            row.createBy(), row.updateBy(), row.createTime(), row.updateTime());
    }

    /** 把领域当前值放入唯一类型槽位，其余保持空值。 */
    public static RiskVariableDefinition row(long id, Entry entry) {
        Definition d = entry.definition();
        var values = typed(d.valueType(), entry.currentValue());
        return new RiskVariableDefinition(id, d.variableCode(), entry.variableName(), entry.description(), d.sourceType(),
            d.valueType(), d.pointCode(), d.sourceContractCode(), d.sourceContractVersion(), d.sourceField(),
            d.usages().contains(VariableUsage.RULE_CONDITION), d.usages().contains(VariableUsage.FLOW_CONDITION),
            d.usages().contains(VariableUsage.ACTION_PARAMETER), d.systemManaged(), d.formatCode(), d.numericUnit(),
            d.currencyCode(), d.currencyVariableCode(), d.allowedValuesVariableCode(), values.stringValue(), values.numberValue(),
            values.booleanValue(), values.datetimeValue(), values.durationValue(), entry.status(), entry.version(),
            entry.createBy(), entry.updateBy(), entry.createTime(), entry.updateTime());
    }

    /** 集合的成员类型由根拥有，不能从非空列猜测另一种类型。 */
    public static Member member(DecisionValueType type, RiskVariableMember row) {
        Object value = value(type, row.stringValue(), row.numberValue(), row.booleanValue(), row.datetimeValue(), row.durationValue(), true);
        if (!VariableValueNormalizer.key(type, value).equals(row.valueKey()) || row.sortOrder() <= 0) {
            throw new ErrorException("集合成员规范键或顺序损坏");
        }
        return new Member(value, row.displayName(), row.sortOrder());
    }

    /** 写入成员规范键和真实类型值，不把成员行标识当作版本。 */
    public static RiskVariableMember memberRow(long id, Definition definition, Member member, Instant time) {
        var values = typed(definition.valueType(), member.value());
        return new RiskVariableMember(id, definition.variableCode(), VariableValueNormalizer.key(definition.valueType(), member.value()),
            values.stringValue(), values.numberValue(), values.booleanValue(), values.datetimeValue(), values.durationValue(),
            member.displayName(), member.sortOrder(), time);
    }

    /** 持久化读取必须满足唯一槽位，防止损坏数据被误读为空值。 */
    static Object value(DecisionValueType type, String text, BigDecimal number, Boolean bool, Instant time, Long duration, boolean required) {
        long count = Stream.of(text, number, bool, time, duration).filter(v -> v != null).count();
        if (count != (required ? 1 : 0)) { throw new ErrorException("变量类型化值槽位损坏"); }
        if (!required) { return null; }
        Object result = switch (type) {
            case STRING -> text;
            case NUMBER -> number == null ? null : number.setScale(8).toPlainString();
            case BOOLEAN -> bool;
            case DATETIME -> time == null ? null : new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time);
            case DURATION -> duration == null ? null : duration.toString();
        };
        if (result == null) { throw new ErrorException("变量值类型与存储槽位不一致"); }
        return result;
    }

    /** 只转换已经通过通用规范化的领域值。 */
    public static Values typed(DecisionValueType type, Object value) {
        if (value == null) { return new Values(null, null, null, null, null); }
        return switch (type) {
            case STRING -> new Values((String) value, null, null, null, null);
            case NUMBER -> new Values(null, new BigDecimal((String) value), null, null, null);
            case BOOLEAN -> new Values(null, null, (Boolean) value, null, null);
            case DATETIME -> new Values(null, null, null, Instant.parse((String) value), null);
            case DURATION -> new Values(null, null, null, null, Long.valueOf((String) value));
        };
    }

    /** 组织规范值对应的五个互斥持久化槽位。 */
    public record Values(String stringValue, BigDecimal numberValue, Boolean booleanValue, Instant datetimeValue, Long durationValue) { }
}
