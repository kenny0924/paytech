package com.paytech.platform.support.workflow.domain.graph;

import java.util.List;

/**
 * 条件子句的比较值：一个标量或一个标量列表，标量统一保存为文本。
 *
 * <p>数字保存为十进制文本，布尔保存为 true / false，日期保存为 YYYY-MM-DD；是否合法由发布校验按变量类型判断。</p>
 *
 * @param values 标量文本；标量形态时恰好一个元素
 * @param list 是否为列表形态
 */
public record ConditionValue(List<String> values, boolean list) {

    /** 复制列表，保持不可变。 */
    public ConditionValue {
        values = List.copyOf(values);
    }

    /**
     * 构造标量比较值。
     *
     * @param value 标量文本
     * @return 标量比较值
     */
    public static ConditionValue scalar(String value) {
        return new ConditionValue(List.of(value), false);
    }

    /**
     * 构造列表比较值。
     *
     * @param values 标量文本列表
     * @return 列表比较值
     */
    public static ConditionValue listOf(List<String> values) {
        return new ConditionValue(values, true);
    }
}
