package com.paytech.operations.console.bff.service.model.riskevaluation;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 发布引用的一个变量及其取值。
 */
@Getter
@Setter
public class RiskEvaluationVariableItem {
    /**
     * 变量编码
     */
    private String variableCode;

    /**
     * 变量名称
     */
    private String variableName;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 值类型
     */
    private String valueType;

    /**
     * 单值
     */
    private String value;

    /**
     * 集合值
     */
    private List<String> values;

    /**
     * 取值依据
     * <li>EVALUATION：评估时的值；CURRENT：常量与集合的当前值，可能与评估时不同。</li>
     */
    private String valueBasis;

    /**
     * Feature 计算状态
     */
    private String technicalStatus;

    /**
     * Feature 失败码
     */
    private String failureCode;

    /**
     * Feature 计算耗时（毫秒）
     */
    private Integer elapsedMillis;
}
