package com.paytech.platform.support.dataquery.template;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

import tools.jackson.databind.JsonNode;

/**
 * 统一模板语法的纯 Java 入口：解析、参数绑定、渲染与保存校验，不依赖 Spring，也不依赖具体引擎。
 *
 * <p>执行路径：{@link #parse} → {@link #bindParameters} → {@link #render}，渲染结果再交给引擎做只读校验与分页改写。
 * 保存路径：{@link #validate} 一次给出全部问题。</p>
 */
public final class QueryTemplateEngine {

    private final TemplateLimits limits;

    private final ParameterBinder parameterBinder;

    /**
     * 以系统硬上限构造。
     *
     * @param limits 硬上限
     */
    public QueryTemplateEngine(TemplateLimits limits) {
        this.limits = limits;
        this.parameterBinder = new ParameterBinder(limits.getMaxListSize());
    }

    /**
     * 解析模板正文。
     *
     * @param body 正文
     * @return 语法树
     * @throws TemplateCheckException 第一处语法错误，带行列与原因
     */
    public QueryTemplate parse(String body) {
        return new TemplateParser().parse(body);
    }

    /**
     * 按声明校验并转换调用方参数，注入系统参数。
     *
     * @param declarations 参数声明
     * @param params 调用方传入的 JSON 对象
     * @param system 系统参数
     * @return 转换结果与字段错误
     */
    public ParameterBindingResult bindParameters(List<QueryParameterDeclaration> declarations, JsonNode params,
        SystemParameters system) {
        return parameterBinder.bind(declarations, params, system);
    }

    /**
     * 渲染模板。
     *
     * @param template 语法树
     * @param values {@link ParameterBindingResult#requireValues()} 的结果
     * @param valueBinder 引擎的值绑定器
     * @return 正文与绑定值
     * @throws TemplateCheckException 取值、比较或绑定失败（如空集合直接写入 #{}），带正文位置
     */
    public RenderedQuery render(QueryTemplate template, Map<String, Object> values, ValueBinder valueBinder) {
        return new TemplateRenderer(valueBinder, BranchSelector.NATURAL).render(template, values);
    }

    /**
     * 保存校验：正文长度、参数声明、语法、引用与类型、未使用参数，再做展开校验。
     *
     * <p>前几步有错误时不做展开校验，避免在不可靠的声明上产生连带问题。数据源状态与约束上限由调用方检查。</p>
     *
     * @param body 正文
     * @param declarations 参数声明
     * @param valueBinder 引擎的值绑定器
     * @param check 引擎对渲染结果的校验
     * @return 问题列表
     */
    public TemplateValidationResult validate(String body, List<QueryParameterDeclaration> declarations,
        ValueBinder valueBinder, RenderedQueryCheck check) {
        List<TemplateProblem> problems = new ArrayList<>();
        if (body == null || body.isBlank()) {
            problems.add(TemplateProblem.error(null, "正文不能为空"));
            return new TemplateValidationResult(List.copyOf(problems));
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > limits.getMaxBodyLength()) {
            problems.add(TemplateProblem.error(null, "正文不能超过 " + limits.getMaxBodyLength() + " 字节"));
            return new TemplateValidationResult(List.copyOf(problems));
        }
        problems.addAll(new TemplateDeclarationChecker(limits.getMaxListSize()).check(declarations));
        QueryTemplate template;
        try {
            template = parse(body);
        } catch (TemplateCheckException exception) {
            problems.add(exception.getProblem());
            return new TemplateValidationResult(List.copyOf(problems));
        }
        TemplateReferenceChecker references = new TemplateReferenceChecker(declarations);
        problems.addAll(references.check(template));
        if (problems.stream().noneMatch(problem -> problem.getLevel() == TemplateProblemLevel.ERROR)) {
            problems.addAll(new TemplateExpansionValidator(template, valueBinder, check,
                limits.getMaxValidationCombinations()).validate(references.referenced(declarations),
                references.cursorReferenced()));
        }
        return new TemplateValidationResult(List.copyOf(problems));
    }
}
