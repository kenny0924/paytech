package com.paytech.operations.console.bff.service.model.riskevaluation;

import lombok.Getter;
import lombok.Setter;

/**
 * 谓词操作数；取自规则集详情，变量操作数补充变量名称。
 */
@Getter
@Setter
public class RiskEvaluationOperand {
    /**
     * 操作数位置
     * <li>LEFT、RIGHT。</li>
     */
    private String operandRole;

    /**
     * 同一位置内的顺序（IN、BETWEEN 等多值）
     */
    private Integer valueSequence;

    /**
     * 来源类型
     * <li>CONTEXT、VARIABLE、LITERAL。</li>
     */
    private String sourceType;

    /**
     * 值类型
     */
    private String valueType;

    /**
     * 输入字段编码；CONTEXT 来源有值
     */
    private String fieldCode;

    /**
     * 变量编码；VARIABLE 来源有值
     */
    private String variableCode;

    /**
     * 变量名称；取解释的变量表，缺失再取规则集详情的当前变量，仍缺失为空
     */
    private String variableName;

    /**
     * 字面值；LITERAL 来源有值，为字符串或布尔
     */
    private Object literalValue;

    /**
     * 数值单位
     * <li>SCALAR、MONEY。</li>
     */
    private String numericUnit;

    /**
     * 金额币种
     */
    private String currencyCode;

    /**
     * 金额币种取自的变量编码
     */
    private String currencyVariableCode;
}
