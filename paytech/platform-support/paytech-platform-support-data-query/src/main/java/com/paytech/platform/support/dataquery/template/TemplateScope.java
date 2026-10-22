package com.paytech.platform.support.dataquery.template;

import java.util.Map;

/** 渲染时的取值作用域：参数值加逐层嵌套的循环变量。 */
final class TemplateScope {

    private final Map<String, Object> parameters;

    private final TemplateScope parent;

    private final String name;

    private final Object value;

    /**
     * 构造作用域节点。
     *
     * @param parameters 参数值（含系统参数）
     * @param parent 外层作用域；顶层为 null
     * @param name 本层变量名；顶层为 null
     * @param value 本层变量值
     */
    private TemplateScope(Map<String, Object> parameters, TemplateScope parent, String name, Object value) {
        this.parameters = parameters;
        this.parent = parent;
        this.name = name;
        this.value = value;
    }

    /**
     * 顶层作用域。
     *
     * @param parameters 参数值
     * @return 作用域
     */
    static TemplateScope of(Map<String, Object> parameters) {
        return new TemplateScope(parameters, null, null, null);
    }

    /**
     * 在本作用域内定义一个循环变量。
     *
     * @param variable 变量名
     * @param variableValue 变量值
     * @return 内层作用域
     */
    TemplateScope with(String variable, Object variableValue) {
        return new TemplateScope(parameters, this, variable, variableValue);
    }

    /**
     * 按路径取值；中间值为 null 时结果为 null。
     *
     * @param path 路径
     * @param position 引用位置，用于报错
     * @return 值
     * @throws TemplateCheckException 引用未声明的名称或对非对象取字段
     */
    Object resolve(TemplatePath path, SourcePosition position) {
        Object current = lookup(path.root(), position);
        String owner = path.root();
        for (String field : path.fields()) {
            if (current == null) {
                return null;
            }
            if (!(current instanceof Map<?, ?> object)) {
                throw new TemplateCheckException(position, owner + " 不是对象，不能取字段 " + field);
            }
            current = object.get(field);
            owner = owner + "." + field;
        }
        return current;
    }

    /**
     * 查找首段名称：先循环变量，后参数。
     *
     * @param root 名称
     * @param position 引用位置
     * @return 值
     */
    private Object lookup(String root, SourcePosition position) {
        for (TemplateScope scope = this; scope.parent != null; scope = scope.parent) {
            if (scope.name.equals(root)) {
                return scope.value;
            }
        }
        if (!parameters.containsKey(root)) {
            throw new TemplateCheckException(position, "引用了未声明的参数：" + root);
        }
        return parameters.get(root);
    }
}
