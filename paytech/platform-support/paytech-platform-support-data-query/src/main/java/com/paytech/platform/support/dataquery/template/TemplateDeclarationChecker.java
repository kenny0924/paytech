package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

/**
 * 参数声明的保存校验：名称、类型与约束的搭配、约束取值、默认值与片段原文，问题按字段路径报告。
 */
final class TemplateDeclarationChecker {

    private static final Set<QueryParameterType> SCALARS = EnumSet.of(QueryParameterType.STRING,
        QueryParameterType.INTEGER, QueryParameterType.DECIMAL, QueryParameterType.BOOLEAN, QueryParameterType.DATE,
        QueryParameterType.DATETIME, QueryParameterType.ENUM);

    private static final Set<QueryParameterType> BOUNDED = EnumSet.of(QueryParameterType.INTEGER,
        QueryParameterType.DECIMAL, QueryParameterType.DATE, QueryParameterType.DATETIME);

    /** 片段原文必须是纯文本：不能含模板标记、语句分隔符或注释符号。 */
    private static final List<String> FRAGMENT_FORBIDDEN = List.of("#{", "@{", "{{", "}}", ";", "--", "/*", "*/",
        "#");

    private final int maxListSize;

    private final ParameterValueConverter converter;

    private final List<TemplateProblem> problems = new ArrayList<>();

    /**
     * 以系统集合上限构造。
     *
     * @param maxListSize 集合元素数硬上限
     */
    TemplateDeclarationChecker(int maxListSize) {
        this.maxListSize = maxListSize;
        this.converter = new ParameterValueConverter(maxListSize);
    }

    /**
     * 校验全部参数声明。
     *
     * @param declarations 参数声明
     * @return 问题，均为 ERROR
     */
    List<TemplateProblem> check(List<QueryParameterDeclaration> declarations) {
        Set<String> names = new HashSet<>();
        for (int index = 0; index < declarations.size(); index++) {
            checkParameter(declarations.get(index), "parameters[" + index + "]", names);
        }
        return problems;
    }

    /**
     * 校验一个顶层参数。
     *
     * @param declaration 声明
     * @param field 字段路径
     * @param names 已出现的参数名
     */
    private void checkParameter(QueryParameterDeclaration declaration, String field, Set<String> names) {
        int before = problems.size();
        checkName(declaration, field, names);
        switch (declaration.getType()) {
            case OBJECT -> error(field + ".type", "OBJECT 只能作为 LIST 的 elementType");
            case LIST -> checkList(declaration, field);
            case FRAGMENT -> checkFragment(declaration, field);
            default -> checkScalar(declaration, declaration.getType(), field);
        }
        if (declaration.getType() != QueryParameterType.LIST) {
            rejectPresent(declaration.getElementType(), field + ".elementType", "只适用于 LIST");
            rejectPresent(declaration.getMaxSize(), field + ".maxSize", "只适用于 LIST");
        }
        if (problems.size() == before) {
            checkDefault(declaration, field);
        }
    }

    /**
     * 名称合法、不以 __ 开头、不重复。
     *
     * @param declaration 声明
     * @param field 字段路径
     * @param names 同级已出现的名称
     */
    private void checkName(QueryParameterDeclaration declaration, String field, Set<String> names) {
        String name = declaration.getName();
        if (!TemplatePath.isName(name)) {
            error(field + ".name", "名称只能由字母、数字和下划线组成，且不能以数字开头");
        } else if (name.startsWith("__")) {
            error(field + ".name", "__ 开头的名称保留给系统参数");
        } else if (!names.add(name)) {
            error(field + ".name", "名称重复：" + name);
        }
    }

    /**
     * 标量的约束搭配：maxLength 仅 STRING，min/max 仅可比较类型，allowedValues 仅 STRING/ENUM 且 ENUM 必填。
     *
     * @param declaration 声明（LIST 时为其自身，约束作用于元素）
     * @param type 标量类型
     * @param field 字段路径
     */
    private void checkScalar(QueryParameterDeclaration declaration, QueryParameterType type, String field) {
        if (declaration.getMaxLength() != null && type != QueryParameterType.STRING) {
            error(field + ".maxLength", "只适用于 STRING");
        }
        checkBounds(declaration, type, field);
        boolean enumerable = type == QueryParameterType.STRING || type == QueryParameterType.ENUM;
        List<String> allowed = declaration.getAllowedValues();
        if (allowed != null && !enumerable) {
            error(field + ".allowedValues", "只适用于 STRING 或 ENUM");
        }
        if (type == QueryParameterType.ENUM && (allowed == null || allowed.isEmpty())) {
            error(field + ".allowedValues", "ENUM 必须声明取值范围");
        }
        rejectPresent(declaration.getChoices(), field + ".choices", "只适用于 FRAGMENT");
        rejectPresent(declaration.getFields(), field + ".fields", "只适用于 elementType 为 OBJECT 的 LIST");
    }

    /**
     * min、max 的适用类型、格式与大小关系。
     *
     * @param declaration 声明
     * @param type 标量类型
     * @param field 字段路径
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void checkBounds(QueryParameterDeclaration declaration, QueryParameterType type, String field) {
        if (declaration.getMin() == null && declaration.getMax() == null) {
            return;
        }
        if (!BOUNDED.contains(type)) {
            error(field + ".min", "min、max 只适用于 INTEGER、DECIMAL、DATE、DATETIME");
            return;
        }
        Object min = bound(declaration.getMin(), type, field + ".min");
        Object max = bound(declaration.getMax(), type, field + ".max");
        if (min instanceof Comparable lower && max != null && lower.compareTo(max) > 0) {
            error(field + ".min", "不能大于 max");
        }
    }

    /**
     * 解析上下限文本。
     *
     * @param text 文本；没有时为 null
     * @param type 类型
     * @param field 字段路径
     * @return 解析值；没有或格式不合法时为 null
     */
    private Object bound(String text, QueryParameterType type, String field) {
        if (text == null) {
            return null;
        }
        Object value = ParameterValueConverter.parseText(type, text);
        if (value == null) {
            error(field, "格式与类型 " + type.getCode() + " 不符");
        }
        return value;
    }

    /**
     * 集合：elementType 合法、maxSize 不超过上限、OBJECT 元素的字段合法。
     *
     * @param declaration 声明
     * @param field 字段路径
     */
    private void checkList(QueryParameterDeclaration declaration, String field) {
        QueryParameterType elementType = declaration.getElementType();
        if (declaration.getMaxSize() != null && declaration.getMaxSize() > maxListSize) {
            error(field + ".maxSize", "不能超过系统上限 " + maxListSize);
        }
        if (elementType == null || elementType == QueryParameterType.LIST
            || elementType == QueryParameterType.FRAGMENT) {
            error(field + ".elementType", "必须声明，且不能是 LIST 或 FRAGMENT");
            return;
        }
        if (elementType != QueryParameterType.OBJECT) {
            checkScalar(declaration, elementType, field);
            return;
        }
        List<QueryParameterDeclaration> fields = declaration.getFields();
        if (fields == null || fields.isEmpty()) {
            error(field + ".fields", "OBJECT 元素必须声明字段");
            return;
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < fields.size(); index++) {
            checkObjectField(fields.get(index), field + ".fields[" + index + "]", names);
        }
    }

    /**
     * 对象字段：只能是标量，不支持默认值。
     *
     * @param declaration 字段声明
     * @param field 字段路径
     * @param names 同级已出现的字段名
     */
    private void checkObjectField(QueryParameterDeclaration declaration, String field, Set<String> names) {
        checkName(declaration, field, names);
        if (!SCALARS.contains(declaration.getType())) {
            error(field + ".type", "对象字段只能是标量类型");
            return;
        }
        checkScalar(declaration, declaration.getType(), field);
        rejectPresent(declaration.getElementType(), field + ".elementType", "只适用于 LIST");
        rejectPresent(declaration.getMaxSize(), field + ".maxSize", "只适用于 LIST");
        rejectPresent(declaration.getDefaultValue(), field + ".default", "对象字段不支持默认值");
    }

    /**
     * 片段：choices 非空，每个取值映射到合法的非空纯文本片段。
     *
     * @param declaration 声明
     * @param field 字段路径
     */
    private void checkFragment(QueryParameterDeclaration declaration, String field) {
        Map<String, String> choices = declaration.getChoices();
        if (choices == null || choices.isEmpty()) {
            error(field + ".choices", "FRAGMENT 必须声明可选值");
            return;
        }
        for (Map.Entry<String, String> choice : choices.entrySet()) {
            String choiceField = field + ".choices." + choice.getKey();
            if (choice.getKey().isBlank() || choice.getValue().isBlank()) {
                error(choiceField, "取值与片段原文都不能为空");
            }
            for (String forbidden : FRAGMENT_FORBIDDEN) {
                if (choice.getValue().contains(forbidden)) {
                    error(choiceField, "片段原文必须是纯文本，不能包含 " + forbidden);
                    break;
                }
            }
        }
        rejectPresent(declaration.getMaxLength(), field + ".maxLength", "只适用于 STRING");
        rejectPresent(declaration.getAllowedValues(), field + ".allowedValues", "只适用于 STRING 或 ENUM");
        rejectPresent(declaration.getMin() != null || declaration.getMax() != null ? declaration : null,
            field + ".min", "min、max 只适用于 INTEGER、DECIMAL、DATE、DATETIME");
        rejectPresent(declaration.getFields(), field + ".fields", "只适用于 elementType 为 OBJECT 的 LIST");
    }

    /**
     * 默认值必须能按声明转换并满足约束；只在声明本身无误时检查。
     *
     * @param declaration 声明
     * @param field 字段路径
     */
    private void checkDefault(QueryParameterDeclaration declaration, String field) {
        if (declaration.getDefaultValue() == null) {
            return;
        }
        List<ParameterError> errors = new ArrayList<>();
        converter.convert(declaration, declaration.getDefaultValue(), field + ".default", errors);
        for (ParameterError parameterError : errors) {
            error(parameterError.getField(), parameterError.getMessage());
        }
    }

    /**
     * 不适用的约束出现时报错。
     *
     * @param value 约束值；null 表示未声明
     * @param field 字段路径
     * @param message 原因
     */
    private void rejectPresent(Object value, String field, String message) {
        if (value != null) {
            error(field, message);
        }
    }

    /**
     * 记录一条错误。
     *
     * @param field 字段路径
     * @param message 原因
     */
    private void error(String field, String message) {
        problems.add(TemplateProblem.field(TemplateProblemLevel.ERROR, field, message));
    }
}
