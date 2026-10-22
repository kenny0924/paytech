package com.paytech.platform.support.workflow.core.enums;

import java.util.EnumSet;
import java.util.Set;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 结构化条件的比较符。
 *
 * <p>每个比较符声明可用的变量类型与比较值形态；文件字段只允许 IS_EMPTY / IS_NOT_EMPTY。</p>
 */
@Getter
public enum ConditionOperator implements CodeEnum {

    /** 等于。 */
    EQ("EQ", ValueShape.SCALAR, EnumSet.of(FormFieldType.TEXT, FormFieldType.LONG_TEXT, FormFieldType.NUMBER,
        FormFieldType.BOOLEAN, FormFieldType.ENUM, FormFieldType.DATE)),

    /** 不等于；变量为空时不成立。 */
    NE("NE", ValueShape.SCALAR, EnumSet.of(FormFieldType.TEXT, FormFieldType.LONG_TEXT, FormFieldType.NUMBER,
        FormFieldType.BOOLEAN, FormFieldType.ENUM, FormFieldType.DATE)),

    /** 属于给定列表之一。 */
    IN("IN", ValueShape.LIST, EnumSet.of(FormFieldType.TEXT, FormFieldType.NUMBER, FormFieldType.ENUM,
        FormFieldType.DATE)),

    /** 文本包含给定子串。 */
    CONTAINS("CONTAINS", ValueShape.SCALAR, EnumSet.of(FormFieldType.TEXT, FormFieldType.LONG_TEXT)),

    /** 大于。 */
    GT("GT", ValueShape.SCALAR, EnumSet.of(FormFieldType.NUMBER, FormFieldType.DATE)),

    /** 大于等于。 */
    GTE("GTE", ValueShape.SCALAR, EnumSet.of(FormFieldType.NUMBER, FormFieldType.DATE)),

    /** 小于。 */
    LT("LT", ValueShape.SCALAR, EnumSet.of(FormFieldType.NUMBER, FormFieldType.DATE)),

    /** 小于等于。 */
    LTE("LTE", ValueShape.SCALAR, EnumSet.of(FormFieldType.NUMBER, FormFieldType.DATE)),

    /** 为空：缺失、null、空字符串或空数组。 */
    IS_EMPTY("IS_EMPTY", ValueShape.NONE, EnumSet.allOf(FormFieldType.class)),

    /** 不为空。 */
    IS_NOT_EMPTY("IS_NOT_EMPTY", ValueShape.NONE, EnumSet.allOf(FormFieldType.class));

    private final String code;

    private final ValueShape valueShape;

    private final Set<FormFieldType> supportedTypes;

    /**
     * 声明比较符的编码、比较值形态与可用类型。
     *
     * @param code 稳定编码
     * @param valueShape 比较值形态
     * @param supportedTypes 可用的变量类型
     */
    ConditionOperator(String code, ValueShape valueShape, Set<FormFieldType> supportedTypes) {
        this.code = code;
        this.valueShape = valueShape;
        this.supportedTypes = supportedTypes;
    }

    /**
     * 判断比较符能否用于该类型的变量。
     *
     * @param type 变量类型
     * @return 可用返回 true
     */
    public boolean supports(FormFieldType type) {
        return supportedTypes.contains(type);
    }

    /** 比较值形态。 */
    @Getter
    @RequiredArgsConstructor
    public enum ValueShape implements CodeEnum {

        /** 不带比较值。 */
        NONE("NONE"),

        /** 一个标量值。 */
        SCALAR("SCALAR"),

        /** 非空标量列表。 */
        LIST("LIST");

        private final String code;
    }
}
