package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** Predicate 的类型化操作数。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RuleOperand {

    /** 规则操作数的稳定业务标识。 */
    private long operandId;

    /** 所属 AST 节点的稳定业务标识。 */
    private long nodeId;

    /** 操作数在比较中的左右角色。 */
    @NonNull
    private OperandRole operandRole;

    /** 同一节点和角色内从一开始的值顺序。 */
    private int valueSequence;

    /** 操作数来源类型。 */
    @NonNull
    private OperandSource sourceType;

    /** FEATURE 来源引用的版本标识；其他来源为空。 */
    private Long featureVersionId;

    /** CONTEXT 来源在所属规则版本输入合同中的字段代码；其他来源为空。 */
    private String fieldCode;

    /** 操作数的闭合值类型。 */
    @NonNull
    private DecisionValueType valueType;

    /** STRING Literal 值；其他形状为空。 */
    private String stringValue;

    /** NUMBER Literal 的 DECIMAL(24,8) 精确值；其他形状为空。 */
    private BigDecimal numberValue;

    /** BOOLEAN Literal 值；其他形状为空。 */
    private Boolean booleanValue;

    /** DATETIME Literal 的 UTC 微秒时间；其他形状为空。 */
    private Instant datetimeValue;

    /** DURATION Literal 的毫秒值；其他形状为空。 */
    private Long durationValue;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 可变记录最后一次持久化更新的 UTC 微秒时间。 */
    @NonNull
    private Instant updateTime;

    /** VARIABLE 引用的永久编码；旧格式与字面量为空。 */
    private String variableCode;

    /** NUMBER 字面量的量纲，变量引用从库定义获取。 */
    private NumericUnit numericUnit;

    /** MONEY 字面量的固定币种，与币种变量互斥。 */
    private String currencyCode;

    /** MONEY 字面量引用的动态币种变量编码。 */
    private String currencyVariableCode;

    /** 原持久化合同保留空的新增列，不重解释历史。 */
    public RuleOperand(long operandId, long nodeId, OperandRole operandRole, int valueSequence,
        OperandSource sourceType, Long featureVersionId, String fieldCode, DecisionValueType valueType,
        String stringValue, BigDecimal numberValue, Boolean booleanValue, Instant datetimeValue,
        Long durationValue, Instant createTime, Instant updateTime) {
        this(operandId, nodeId, operandRole, valueSequence, sourceType, featureVersionId, fieldCode, valueType,
            stringValue, numberValue, booleanValue, datetimeValue, durationValue, createTime, updateTime,
            null, null, null, null);
    }

}
