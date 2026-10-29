package com.paytech.platform.support.workflow.domain.action;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;

/**
 * DATA_QUERY 动作声明的一个输出：读取查询结果列 {@code out_<name>}，写入变量 {@code <actionKey>.<name>}。
 *
 * @param name 输出名，同时是 output_json 的属性名
 * @param type 变量类型：TEXT、NUMBER、BOOLEAN、ENUM 或 DATE（其他类型由配置校验拒绝）
 * @param enumValues ENUM 的可选值；其他类型为空列表
 */
public record DataQueryOutput(String name, FormFieldType type, List<String> enumValues) {

    /** 结果列名前缀。 */
    public static final String COLUMN_PREFIX = "out_";

    /** 复制列表，保持不可变。 */
    public DataQueryOutput {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    /**
     * 对应的查询结果列名。
     *
     * @return out_ 加输出名
     */
    public String column() {
        return COLUMN_PREFIX + name;
    }
}
