package com.paytech.platform.support.workflow.domain.variable;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;

/**
 * 一个实例变量的声明：变量名、类型与枚举选项。
 *
 * <p>变量名为 {@code 前缀.名称}：启动表单前缀为 start，动作输出前缀为动作 key；名称对应 start_values_json
 * 或 output_json 中的同名属性。</p>
 *
 * @param name 完整变量名
 * @param type 变量类型
 * @param enumValues ENUM 的可选值；其他类型为空列表
 */
public record VariableDeclaration(String name, FormFieldType type, List<String> enumValues) {

    /** 启动表单变量的前缀。 */
    public static final String START_PREFIX = "start";

    /** 复制列表，保持不可变。 */
    public VariableDeclaration {
        enumValues = List.copyOf(enumValues);
    }

    /**
     * 声明非枚举变量。
     *
     * @param name 完整变量名
     * @param type 变量类型
     * @return 变量声明
     */
    public static VariableDeclaration of(String name, FormFieldType type) {
        return new VariableDeclaration(name, type, List.of());
    }

    /**
     * 把表单字段声明为以给定前缀开头的变量。
     *
     * @param prefix 变量前缀：start 或动作 key
     * @param form 表单定义
     * @return 每个字段一个变量声明
     */
    public static List<VariableDeclaration> ofForm(String prefix, FormDefinition form) {
        return form.fields().stream()
            .map(field -> new VariableDeclaration(prefix + "." + field.key(), field.type(),
                field.rules().options().stream().map(option -> option.value()).toList()))
            .toList();
    }

    /**
     * 变量前缀：start 或动作 key。
     *
     * @return 第一个点之前的部分
     */
    public String prefix() {
        return name.substring(0, name.indexOf('.'));
    }

    /**
     * 变量在启动值或动作输出 JSON 中的属性名。
     *
     * @return 第一个点之后的部分
     */
    public String localName() {
        return name.substring(name.indexOf('.') + 1);
    }
}
