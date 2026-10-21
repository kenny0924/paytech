package com.paytech.channel.connector.protocol.domainservice.validator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.domain.RegisteredFunctionCatalog;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionEngine;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionInvalidException;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionProfile;

import lombok.AllArgsConstructor;

import tools.jackson.databind.JsonNode;

/**
 * 取值绑定 ValueBinding（PATH / EXPRESSION / FUNCTION）的静态规则，原子 API 与流程共用。
 *
 * <p>表达式按只读剖面编译（研发计划第 3 步第 4 节），只能读取上下文根下的变量；
 * 再从表达式文本提取上下文引用，由调用方判断引用是否允许。
 */
@Component
@AllArgsConstructor
public class ValueBindingValidator {

    /** 单个表达式的最大字符数。 */
    public static final int MAX_EXPRESSION = 4096;

    /** 可被引用的上下文根。 */
    static final Set<String> ROOTS = Set.of("input", "resources", "steps", "params", "http", "result");

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern REFERENCE = Pattern.compile(
        "(?<![A-Za-z0-9_$.])(input|resources|steps|params|http|result)((?:\\.[A-Za-z_$][A-Za-z0-9_$]*|\\[[^\\]]*])*)");
    private static final Pattern PATH = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    final ExpressionEngine expressionEngine;

    /**
     * 校验一个取值绑定。
     *
     * @param node 绑定 JSON
     * @param location 问题定位路径
     * @param allowFunction 是否允许 FUNCTION
     * @param reference 判断某个引用路径（含根）是否允许，返回 null 表示允许，否则为拒绝说明
     * @param issues 问题收集
     * @return 绑定中出现的全部引用路径
     */
    public List<String> validate(JsonNode node, String location, boolean allowFunction,
        java.util.function.Function<String, String> reference, List<ValidationIssue> issues) {
        var references = new ArrayList<String>();
        if (node == null || !node.isObject() || !node.path("kind").isString()) {
            issues.add(new ValidationIssue("BINDING_INVALID", location, "取值必须是包含 kind 的对象"));
            return references;
        }
        switch (node.get("kind").asString()) {
            case "PATH" -> path(node, location, reference, references, issues);
            case "EXPRESSION" -> expression(node, location, reference, references, issues);
            case "FUNCTION" -> {
                if (allowFunction) {
                    function(node, location, reference, references, issues);
                } else {
                    issues.add(new ValidationIssue("BINDING_KIND_NOT_ALLOWED", location + ".kind", "此处不允许 FUNCTION"));
                }
            }
            default -> issues.add(new ValidationIssue("BINDING_KIND_INVALID", location + ".kind",
                "kind 只允许 PATH、EXPRESSION、FUNCTION"));
        }
        return references;
    }

    /** 判断表达式是否为常量：不引用任何上下文根。 */
    public boolean constant(String expression) {
        return references(expression).isEmpty();
    }

    /** 从表达式文本中提取上下文引用；字符串字面量内的文字不计入。 */
    public List<String> references(String expression) {
        String code = STRING_LITERAL.matcher(expression).replaceAll("''");
        var result = new LinkedHashSet<String>();
        Matcher matcher = REFERENCE.matcher(code);
        while (matcher.find()) {
            result.add(matcher.group(1) + matcher.group(2));
        }
        return List.copyOf(result);
    }

    /** PATH：只读点分路径，不允许数组下标或 []。 */
    void path(JsonNode node, String location, java.util.function.Function<String, String> reference,
        List<String> references, List<ValidationIssue> issues) {
        if (!keys(node, location, Set.of("kind", "path"), issues)) {
            return;
        }
        String value = node.path("path").isString() ? node.get("path").asString() : null;
        if (value == null || !PATH.matcher(value).matches() || value.indexOf('.') < 0) {
            issues.add(new ValidationIssue("BINDING_PATH_INVALID", location + ".path",
                "路径必须是以上下文根开头的点分路径，不能使用下标或 []"));
            return;
        }
        check(value, location + ".path", reference, issues);
        references.add(value);
    }

    /** EXPRESSION：非空且不超长，提取引用逐个检查。 */
    void expression(JsonNode node, String location, java.util.function.Function<String, String> reference,
        List<String> references, List<ValidationIssue> issues) {
        if (!keys(node, location, Set.of("kind", "expression"), issues)) {
            return;
        }
        String value = expressionText(node.get("expression"), location + ".expression", issues);
        if (value == null) {
            return;
        }
        for (String path : references(value)) {
            check(path, location + ".expression", reference, issues);
            references.add(path);
        }
    }

    /** FUNCTION：已注册纯函数，参数完整且不嵌套 FUNCTION，result 为表达式。 */
    void function(JsonNode node, String location, java.util.function.Function<String, String> reference,
        List<String> references, List<ValidationIssue> issues) {
        if (!keys(node, location, Set.of("kind", "function", "args", "result"), issues)) {
            return;
        }
        String code = node.path("function").isString() ? node.get("function").asString() : "";
        var function = RegisteredFunctionCatalog.find(code);
        if (function.isEmpty() || !RegisteredFunctionCatalog.PURE.equals(function.get().getKind())) {
            issues.add(new ValidationIssue("FUNCTION_NOT_ALLOWED", location + ".function", "函数未注册或不是纯函数：" + code));
            return;
        }
        JsonNode args = node.get("args");
        if (args == null || !args.isObject()) {
            issues.add(new ValidationIssue("FUNCTION_ARGS_INVALID", location + ".args", "args 必须是对象"));
            return;
        }
        var declared = function.get().getArgs();
        for (String name : args.propertyNames()) {
            if (declared.stream().noneMatch(parameter -> parameter.getName().equals(name))) {
                issues.add(new ValidationIssue("FUNCTION_ARG_UNKNOWN", location + ".args." + name, "函数没有参数 " + name));
                continue;
            }
            references.addAll(validate(args.get(name), location + ".args." + name, false, reference, issues));
        }
        declared.stream().filter(parameter -> parameter.isRequired() && !args.has(parameter.getName()))
            .forEach(parameter -> issues.add(new ValidationIssue("FUNCTION_ARG_MISSING",
                location + ".args." + parameter.getName(), "缺少必填函数参数")));
        String result = expressionText(node.get("result"), location + ".result", issues);
        if (result != null && references(result).stream().anyMatch(path -> !path.equals("result")
            && !path.startsWith("result."))) {
            issues.add(new ValidationIssue("FUNCTION_RESULT_INVALID", location + ".result",
                "result 表达式只能引用 result"));
        }
    }

    /** 读取非空、不超长且能按只读剖面编译的表达式文本。 */
    String expressionText(JsonNode node, String location, List<ValidationIssue> issues) {
        if (node == null || !node.isString() || node.asString().isBlank()) {
            issues.add(new ValidationIssue("EXPRESSION_EMPTY", location, "表达式不能为空"));
            return null;
        }
        if (node.asString().length() > MAX_EXPRESSION) {
            issues.add(new ValidationIssue("EXPRESSION_TOO_LONG", location, "表达式最多 " + MAX_EXPRESSION + " 字符"));
            return null;
        }
        String problem = compileProblem(node.asString());
        if (problem != null) {
            issues.add(new ValidationIssue("EXPRESSION_INVALID", location, problem));
            return null;
        }
        return node.asString();
    }

    /** 按只读剖面编译并检查变量都在上下文根下；返回 null 表示通过，否则为问题说明。 */
    String compileProblem(String expression) {
        try {
            for (String variable : expressionEngine.compile(ExpressionProfile.READ_ONLY, expression).getVariableFullNames()) {
                String root = variable.contains(".") ? variable.substring(0, variable.indexOf('.')) : variable;
                if (!ROOTS.contains(root)) {
                    return "表达式引用了未知变量：" + variable;
                }
            }
            return null;
        } catch (ExpressionInvalidException exception) {
            return exception.getMessage();
        }
    }

    /** 对象只允许指定属性。 */
    boolean keys(JsonNode node, String location, Set<String> allowed, List<ValidationIssue> issues) {
        var unknown = node.propertyNames().stream().filter(Predicate.not(allowed::contains)).toList();
        unknown.forEach(name -> issues.add(new ValidationIssue("PROPERTY_UNKNOWN", location + "." + name, "不支持的属性")));
        return unknown.isEmpty();
    }

    /** 交给调用方判断引用是否允许。 */
    void check(String path, String location, java.util.function.Function<String, String> reference,
        List<ValidationIssue> issues) {
        String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
        if (!ROOTS.contains(root)) {
            issues.add(new ValidationIssue("REFERENCE_ROOT_INVALID", location, "引用的上下文根不受支持：" + root));
            return;
        }
        String problem = reference.apply(path);
        if (problem != null) {
            issues.add(new ValidationIssue("REFERENCE_NOT_ALLOWED", location, problem + "：" + path));
        }
    }
}
