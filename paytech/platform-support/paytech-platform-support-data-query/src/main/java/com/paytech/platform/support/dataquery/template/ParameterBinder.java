package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

import tools.jackson.databind.JsonNode;

/**
 * 按声明校验并转换调用方参数，再注入系统参数。
 *
 * <p>未声明参数与 __ 开头参数直接拒绝；缺失时取默认值，没有默认值时为 null（LIST 为空集合），必填缺失报错。</p>
 */
public final class ParameterBinder {

    private static final String ROOT = "params";

    private final ParameterValueConverter converter;

    /**
     * 以系统集合上限构造。
     *
     * @param maxListSize 集合元素数硬上限（data-query.limits.max-list-size）
     */
    public ParameterBinder(int maxListSize) {
        this.converter = new ParameterValueConverter(maxListSize);
    }

    /**
     * 校验并转换参数。
     *
     * @param declarations 参数声明
     * @param params 调用方传入的 JSON 对象；null 或缺失视为空对象
     * @param system 服务端注入的系统参数
     * @return 转换结果与字段错误
     */
    public ParameterBindingResult bind(List<QueryParameterDeclaration> declarations, JsonNode params,
        SystemParameters system) {
        List<ParameterError> errors = new ArrayList<>();
        JsonNode input = params == null || params.isMissingNode() || params.isNull() ? null : params;
        rejectUndeclared(declarations, input, errors);
        Map<String, Object> values = new LinkedHashMap<>();
        for (QueryParameterDeclaration declaration : declarations) {
            values.put(declaration.getName(), bindOne(declaration, input, errors));
        }
        values.put(SystemParameters.OPERATOR,
            Collections.singletonMap(SystemParameters.OPERATOR_ACCOUNT_ID, system.getOperatorAccountId()));
        values.put(SystemParameters.CURSOR, system.getCursor());
        return new ParameterBindingResult(Collections.unmodifiableMap(values), List.copyOf(errors));
    }

    /**
     * 拒绝非对象入参、未声明参数与调用方传入的系统参数。
     *
     * @param declarations 参数声明
     * @param input 入参；可为 null
     * @param errors 错误收集
     */
    private static void rejectUndeclared(List<QueryParameterDeclaration> declarations, JsonNode input,
        List<ParameterError> errors) {
        if (input == null) {
            return;
        }
        if (!input.isObject()) {
            errors.add(new ParameterError(ROOT, "必须是 JSON 对象"));
            return;
        }
        Set<String> declared = declarations.stream().map(QueryParameterDeclaration::getName).collect(Collectors.toSet());
        for (String name : input.propertyNames()) {
            if (name.startsWith("__")) {
                errors.add(new ParameterError(ROOT + "." + name, "系统参数只能由服务端注入"));
            } else if (!declared.contains(name)) {
                errors.add(new ParameterError(ROOT + "." + name, "未声明的参数"));
            }
        }
    }

    /**
     * 校验并转换一个参数。
     *
     * @param declaration 声明
     * @param input 入参对象；可为 null
     * @param errors 错误收集
     * @return 转换后的值
     */
    private Object bindOne(QueryParameterDeclaration declaration, JsonNode input, List<ParameterError> errors) {
        String field = ROOT + "." + declaration.getName();
        JsonNode node = input == null || !input.isObject() ? null : input.get(declaration.getName());
        if (node == null || node.isNull()) {
            node = declaration.getDefaultValue();
        }
        if (node == null || node.isNull()) {
            ParameterValueConverter.addMissing(declaration, field, errors);
            return declaration.getType() == QueryParameterType.LIST ? List.of() : null;
        }
        return converter.convert(declaration, node, field, errors);
    }
}
