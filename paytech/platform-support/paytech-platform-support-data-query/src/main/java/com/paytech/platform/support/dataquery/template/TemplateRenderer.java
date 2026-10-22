package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 遍历语法树生成 {@link RenderedQuery}：文本原样拼接，值交给值绑定器，片段输出固定原文。
 */
final class TemplateRenderer {

    private final ValueBinder valueBinder;

    private final BranchSelector branchSelector;

    /**
     * 以值绑定器与分支选择构造。
     *
     * @param valueBinder 引擎的值绑定器
     * @param branchSelector 分支选择；执行时为 {@link BranchSelector#NATURAL}
     */
    TemplateRenderer(ValueBinder valueBinder, BranchSelector branchSelector) {
        this.valueBinder = valueBinder;
        this.branchSelector = branchSelector;
    }

    /**
     * 渲染模板。
     *
     * @param template 语法树
     * @param values 参数值（含系统参数）
     * @return 渲染结果
     * @throws TemplateCheckException 取值、比较或绑定失败，带正文位置
     */
    RenderedQuery render(QueryTemplate template, Map<String, Object> values) {
        RenderOutput output = new RenderOutput();
        renderNodes(template.getNodes(), TemplateScope.of(values), output);
        return output.toRenderedQuery();
    }

    /**
     * 依次渲染节点。
     *
     * @param nodes 节点
     * @param scope 作用域
     * @param output 输出
     */
    private void renderNodes(List<TemplateNode> nodes, TemplateScope scope, RenderOutput output) {
        for (TemplateNode node : nodes) {
            renderNode(node, scope, output);
        }
    }

    /**
     * 渲染一个节点。
     *
     * @param node 节点
     * @param scope 作用域
     * @param output 输出
     */
    private void renderNode(TemplateNode node, TemplateScope scope, RenderOutput output) {
        switch (node) {
            case TextNode text -> output.appendLiteral(text.getText(), text.getPosition());
            case ValueNode value -> renderValue(value, scope, output);
            case FragmentNode fragment -> renderFragment(fragment, scope, output);
            case IfNode ifNode -> renderIf(ifNode, scope, output);
            case EachNode each -> renderEach(each, scope, output);
            case JoinNode join -> renderJoin(join, scope, output);
            case PartNode part -> renderNodes(part.getChildren(), scope, output);
        }
    }

    /**
     * 值节点交给绑定器，绑定器的拒绝补上正文位置。
     *
     * @param node 值节点
     * @param scope 作用域
     * @param output 输出
     */
    private void renderValue(ValueNode node, TemplateScope scope, RenderOutput output) {
        Object value = scope.resolve(node.getPath(), node.getPosition());
        String text;
        try {
            text = valueBinder.bind(value, output.bindings());
        } catch (TemplateCheckException exception) {
            throw exception;
        } catch (BizCheckFailException exception) {
            throw new TemplateCheckException(TemplateProblem.error(node.getPosition(),
                "#{" + node.getPath().text() + "}：" + exception.getMessage()), exception);
        }
        output.appendFixed(text, node.getPosition());
    }

    /**
     * 片段节点输出所选片段原文。
     *
     * @param node 片段节点
     * @param scope 作用域
     * @param output 输出
     */
    private void renderFragment(FragmentNode node, TemplateScope scope, RenderOutput output) {
        Object value = scope.resolve(TemplatePath.of(node.getName()), node.getPosition());
        if (!(value instanceof SelectedFragment fragment)) {
            throw new TemplateCheckException(node.getPosition(), "@{" + node.getName() + "} 没有可输出的片段取值");
        }
        output.appendFixed(fragment.getText(), node.getPosition());
    }

    /**
     * 条件块：按顺序取第一个成立的分支，再经分支选择钩子确定走向。
     *
     * @param node 条件块
     * @param scope 作用域
     * @param output 输出
     */
    private void renderIf(IfNode node, TemplateScope scope, RenderOutput output) {
        List<IfBranch> branches = node.getBranches();
        int natural = branches.size();
        for (int index = 0; index < branches.size(); index++) {
            TemplateExpression condition = branches.get(index).getCondition();
            if (condition == null || ExpressionEvaluator.test(condition, scope)) {
                natural = index;
                break;
            }
        }
        int selected = branchSelector.select(node, natural);
        if (selected < branches.size()) {
            renderNodes(branches.get(selected).getChildren(), scope, output);
        }
    }

    /**
     * 循环块：null 视为空集合，元素之间输出分隔符，下标从 0 开始。
     *
     * @param node 循环块
     * @param scope 作用域
     * @param output 输出
     */
    private void renderEach(EachNode node, TemplateScope scope, RenderOutput output) {
        Object value = scope.resolve(node.getPath(), node.getPosition());
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> items)) {
            throw new TemplateCheckException(node.getPosition(), "{{#each}} 只能遍历集合：" + node.getPath().text());
        }
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                output.appendFixed(node.getSeparator(), node.getPosition());
            }
            TemplateScope itemScope = scope.with(node.getItemName(), items.get(index));
            if (node.getIndexName() != null) {
                itemScope = itemScope.with(node.getIndexName(), (long) index);
            }
            renderNodes(node.getChildren(), itemScope, output);
        }
    }

    /**
     * 拼接块：每个非空白子节点为一段，去掉首尾空白后丢弃空段，其余用分隔符连接；全部为空时不输出前后缀。
     *
     * @param node 拼接块
     * @param scope 作用域
     * @param output 输出
     */
    private void renderJoin(JoinNode node, TemplateScope scope, RenderOutput output) {
        List<RenderOutput> segments = new ArrayList<>();
        for (TemplateNode child : node.getChildren()) {
            if (child instanceof TextNode) {
                continue;
            }
            RenderOutput segment = new RenderOutput();
            renderNode(child, scope, segment);
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            return;
        }
        output.appendFixed(node.getPrefix(), node.getPosition());
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) {
                output.appendFixed(node.getSeparator(), node.getPosition());
            }
            output.appendTrimmed(segments.get(index));
        }
        output.appendFixed(node.getSuffix(), node.getPosition());
    }
}
