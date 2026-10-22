package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模板条件表达式的比较运算符；编码即模板中的符号。 */
@Getter
@RequiredArgsConstructor
public enum TemplateComparisonOperator implements CodeEnum {

    /** 等于。 */
    EQ("=="),

    /** 不等于。 */
    NE("!="),

    /** 大于。 */
    GT(">"),

    /** 大于等于。 */
    GE(">="),

    /** 小于。 */
    LT("<"),

    /** 小于等于。 */
    LE("<=");

    private final String code;

    /**
     * 是否为大小比较；大小比较不接受 null 字面量与布尔值。
     *
     * @return true 表示 &gt; &gt;= &lt; &lt;=
     */
    public boolean ordering() {
        return this != EQ && this != NE;
    }
}
