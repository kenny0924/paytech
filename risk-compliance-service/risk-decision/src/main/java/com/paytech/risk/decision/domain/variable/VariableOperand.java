package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;

/** 规则与流程共用的变量操作数；声明字段只属于配置，不保存运行值。 */
public record VariableOperand(OperandRole operandRole, int valueSequence, OperandSource sourceType,
    String variableCode, String fieldCode, DecisionValueType valueType, LiteralValue literalValue,
    NumericUnit numericUnit, String currencyCode, String currencyVariableCode) { }
