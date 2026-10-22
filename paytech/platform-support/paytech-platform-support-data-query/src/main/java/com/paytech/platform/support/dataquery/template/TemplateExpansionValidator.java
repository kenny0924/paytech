package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

/**
 * 展开校验：列举参数取值组合渲染模板，每个结果交给引擎校验，问题定位回模板行列。
 *
 * <p>维度为每个参数的候选值（见 {@link ExpansionSamples#options}）与是否有游标。组合数不超过上限时全部列举；
 * 超过时改用覆盖集——第 k 个组合在每个维度取第 k mod n 个候选值，保证每个候选值至少出现一次。
 * 之后对仍未走到的条件分支（含“都不成立”），以“集合取一个元素、其余取首个样例”为基础强制走该分支及其外层分支再渲染一次。
 * 这一步只为尽早发现问题，最终防线仍是每次执行前的校验。</p>
 */
final class TemplateExpansionValidator {

    private final QueryTemplate template;

    private final ValueBinder valueBinder;

    private final RenderedQueryCheck check;

    private final int maxCombinations;

    private final Map<IfNode, BitSet> taken = new IdentityHashMap<>();

    private final Map<IfNode, List<Map.Entry<IfNode, Integer>>> ancestry = new IdentityHashMap<>();

    private final List<IfNode> ifNodes = new ArrayList<>();

    private final Set<String> problemKeys = new LinkedHashSet<>();

    private final List<TemplateProblem> problems = new ArrayList<>();

    /** 一个展开维度：参数名与候选值。 */
    private static final class Dimension {

        /** 参数名。 */
        private final String name;

        /** 候选值，可含 null。 */
        private final List<Object> options;

        /** 强制分支时使用的候选值下标。 */
        private final int baseIndex;

        /**
         * 构造维度。
         *
         * @param name 参数名
         * @param options 候选值
         * @param baseIndex 强制分支时使用的候选值下标
         */
        private Dimension(String name, List<Object> options, int baseIndex) {
            this.name = name;
            this.options = options;
            this.baseIndex = baseIndex;
        }
    }

    /**
     * 构造校验器。
     *
     * @param template 语法树，已通过引用检查
     * @param valueBinder 引擎的值绑定器
     * @param check 引擎对渲染结果的校验
     * @param maxCombinations 全列举的组合数上限
     */
    TemplateExpansionValidator(QueryTemplate template, ValueBinder valueBinder, RenderedQueryCheck check,
        int maxCombinations) {
        this.template = template;
        this.valueBinder = valueBinder;
        this.check = check;
        this.maxCombinations = maxCombinations;
    }

    /**
     * 执行展开校验。
     *
     * @param declarations 正文引用的参数声明，已通过声明校验；未引用的参数不影响渲染，不参与列举
     * @param cursorReferenced 正文是否引用了 __cursor
     * @return 问题：渲染或引擎校验失败为 ERROR，改用覆盖集为 INFO
     */
    List<TemplateProblem> validate(List<QueryParameterDeclaration> declarations, boolean cursorReferenced) {
        collectIfNodes(template.getNodes(), List.of());
        List<Dimension> dimensions = dimensions(declarations, cursorReferenced);
        long total = 1;
        int widest = 1;
        for (Dimension dimension : dimensions) {
            int size = dimension.options.size();
            total = total > Long.MAX_VALUE / size ? Long.MAX_VALUE : total * size;
            widest = Math.max(widest, dimension.options.size());
        }
        boolean full = total <= maxCombinations;
        long count = full ? total : widest;
        if (!full) {
            add(TemplateProblem.at(TemplateProblemLevel.INFO, null,
                "组合较多（共 " + total + " 种），已按覆盖集校验 " + count + " 种"));
        }
        for (long combination = 0; combination < count; combination++) {
            renderAndInspect(values(dimensions, combination, full), Map.of());
        }
        coverRemainingBranches(dimensions);
        return problems;
    }

    /**
     * 构造维度。
     *
     * @param declarations 参数声明
     * @param cursorReferenced 是否引用游标
     * @return 维度，参数在前、游标在后
     */
    private static List<Dimension> dimensions(List<QueryParameterDeclaration> declarations, boolean cursorReferenced) {
        List<Dimension> dimensions = new ArrayList<>();
        for (QueryParameterDeclaration declaration : declarations) {
            List<Object> options = ExpansionSamples.options(declaration);
            int baseIndex = declaration.getType() == QueryParameterType.LIST ? 1 : 0;
            dimensions.add(new Dimension(declaration.getName(), options, baseIndex));
        }
        List<Object> cursorOptions = new ArrayList<>();
        cursorOptions.add(cursorReferenced ? new ExpansionSamples.SampleCursor() : null);
        if (cursorReferenced) {
            cursorOptions.add(null);
        }
        dimensions.add(new Dimension(SystemParameters.CURSOR, cursorOptions, 0));
        return dimensions;
    }

    /**
     * 第 combination 个组合的参数值。
     *
     * @param dimensions 维度
     * @param combination 组合序号
     * @param full true 按混合进制全列举，false 按覆盖集取 combination mod n
     * @return 参数值（含系统参数）
     */
    private static Map<String, Object> values(List<Dimension> dimensions, long combination, boolean full) {
        Map<String, Object> values = baseSystemValues();
        long remainder = combination;
        for (Dimension dimension : dimensions) {
            int size = dimension.options.size();
            int index = (int) (full ? remainder % size : combination % size);
            remainder = remainder / size;
            values.put(dimension.name, dimension.options.get(index));
        }
        return values;
    }

    /**
     * 系统参数的固定样例：操作人账号 1。
     *
     * @return 可继续写入的参数值
     */
    private static Map<String, Object> baseSystemValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(SystemParameters.OPERATOR, Collections.singletonMap(SystemParameters.OPERATOR_ACCOUNT_ID, 1L));
        return values;
    }

    /**
     * 记录全部条件块及其外层分支。
     *
     * @param nodes 节点
     * @param ancestors 外层条件块与所在分支
     */
    private void collectIfNodes(List<TemplateNode> nodes, List<Map.Entry<IfNode, Integer>> ancestors) {
        for (TemplateNode node : nodes) {
            switch (node) {
                case IfNode ifNode -> collectIf(ifNode, ancestors);
                case EachNode each -> collectIfNodes(each.getChildren(), ancestors);
                case JoinNode join -> collectIfNodes(join.getChildren(), ancestors);
                case PartNode part -> collectIfNodes(part.getChildren(), ancestors);
                default -> {
                }
            }
        }
    }

    /**
     * 记录一个条件块，再进入各分支。
     *
     * @param node 条件块
     * @param ancestors 外层条件块与所在分支
     */
    private void collectIf(IfNode node, List<Map.Entry<IfNode, Integer>> ancestors) {
        ifNodes.add(node);
        ancestry.put(node, ancestors);
        for (int index = 0; index < node.getBranches().size(); index++) {
            List<Map.Entry<IfNode, Integer>> inner = new ArrayList<>(ancestors);
            inner.add(Map.entry(node, index));
            collectIfNodes(node.getBranches().get(index).getChildren(), List.copyOf(inner));
        }
    }

    /**
     * 对尚未走到的分支强制渲染一次。
     *
     * @param dimensions 维度
     */
    private void coverRemainingBranches(List<Dimension> dimensions) {
        Map<String, Object> base = baseSystemValues();
        for (Dimension dimension : dimensions) {
            base.put(dimension.name, dimension.options.get(Math.min(dimension.baseIndex, dimension.options.size() - 1)));
        }
        for (IfNode node : ifNodes) {
            for (int outcome = 0; outcome < node.outcomeCount(); outcome++) {
                if (taken.containsKey(node) && taken.get(node).get(outcome)) {
                    continue;
                }
                Map<IfNode, Integer> forced = new IdentityHashMap<>();
                ancestry.get(node).forEach(entry -> forced.put(entry.getKey(), entry.getValue()));
                forced.put(node, outcome);
                renderAndInspect(base, forced);
            }
        }
    }

    /**
     * 渲染一个组合并交给引擎校验；记录走到的分支。
     *
     * @param values 参数值
     * @param forced 强制走向的条件块
     */
    private void renderAndInspect(Map<String, Object> values, Map<IfNode, Integer> forced) {
        BranchSelector selector = (node, natural) -> {
            int chosen = forced.getOrDefault(node, natural);
            taken.computeIfAbsent(node, ignored -> new BitSet()).set(chosen);
            return chosen;
        };
        RenderedQuery rendered;
        try {
            rendered = new TemplateRenderer(valueBinder, selector).render(template, values);
        } catch (TemplateCheckException exception) {
            add(exception.getProblem());
            return;
        }
        for (RenderedQueryViolation violation : check.inspect(rendered)) {
            add(TemplateProblem.error(rendered.locate(violation.getOffset()), violation.getMessage()));
        }
    }

    /**
     * 去重后记录问题。
     *
     * @param problem 问题
     */
    private void add(TemplateProblem problem) {
        String key = problem.getLevel() + "|" + problem.getLine() + "|" + problem.getColumn() + "|"
            + problem.getField() + "|" + problem.getMessage();
        if (problemKeys.add(key)) {
            problems.add(problem);
        }
    }
}
