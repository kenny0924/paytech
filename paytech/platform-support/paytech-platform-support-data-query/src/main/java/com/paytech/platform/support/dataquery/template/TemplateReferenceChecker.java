package com.paytech.platform.support.dataquery.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateComparisonOperator;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;

/**
 * 正文的静态检查：引用的路径都已声明、是当前作用域的循环变量或已知系统参数；值与片段占位的类型合适；
 * 比较两边类型兼容；声明了但未使用的参数给出警告。
 */
final class TemplateReferenceChecker {

    /** 系统参数 __operator：只有 accountId 一个字段。 */
    private static final QueryParameterDeclaration OPERATOR = QueryParameterDeclaration.builder()
        .name(SystemParameters.OPERATOR).type(QueryParameterType.OBJECT)
        .fields(List.of(QueryParameterDeclaration.builder().name(SystemParameters.OPERATOR_ACCOUNT_ID)
            .type(QueryParameterType.INTEGER).build()))
        .build();

    /** 系统参数 __cursor：字段由模板的 cursorColumns 决定，类型未知。 */
    private static final QueryParameterDeclaration CURSOR = QueryParameterDeclaration.builder()
        .name(SystemParameters.CURSOR).type(QueryParameterType.OBJECT).build();

    /** 循环下标。 */
    private static final QueryParameterDeclaration INDEX = QueryParameterDeclaration.builder().name("index")
        .type(QueryParameterType.INTEGER).build();

    /** 类型未知的取值（游标字段）。 */
    private static final QueryParameterDeclaration ANY = QueryParameterDeclaration.builder().name("*").build();

    private static final String ANY_KIND = "任意";

    private static final String NULL_KIND = "null";

    private final Map<String, QueryParameterDeclaration> declarations = new LinkedHashMap<>();

    private final Set<String> used = new HashSet<>();

    private final List<TemplateProblem> problems = new ArrayList<>();

    private boolean cursorReferenced;

    /**
     * 以参数声明构造。
     *
     * @param declarations 参数声明
     */
    TemplateReferenceChecker(List<QueryParameterDeclaration> declarations) {
        for (QueryParameterDeclaration declaration : declarations) {
            this.declarations.putIfAbsent(declaration.getName(), declaration);
        }
    }

    /**
     * 检查模板。
     *
     * @param template 语法树
     * @return 问题：引用与类型错误为 ERROR，未使用参数为 WARNING
     */
    List<TemplateProblem> check(QueryTemplate template) {
        checkNodes(template.getNodes(), Map.of());
        int index = 0;
        for (String name : declarations.keySet()) {
            if (!used.contains(name)) {
                problems.add(TemplateProblem.field(TemplateProblemLevel.WARNING, "parameters[" + index + "]",
                    "参数 " + name + " 已声明但未在正文中使用"));
            }
            index++;
        }
        return problems;
    }

    /**
     * 正文实际引用的参数，展开校验只对它们列举取值。
     *
     * @param declarations 参数声明
     * @return 被引用的声明，保持声明顺序
     */
    List<QueryParameterDeclaration> referenced(List<QueryParameterDeclaration> declarations) {
        return declarations.stream().filter(declaration -> used.contains(declaration.getName())).toList();
    }

    /**
     * 正文是否引用了 __cursor，展开校验据此增加“有 / 无游标”两种取值。
     *
     * @return true 表示引用了游标
     */
    boolean cursorReferenced() {
        return cursorReferenced;
    }

    /**
     * 检查节点列表。
     *
     * @param nodes 节点
     * @param scope 当前可见的循环变量
     */
    private void checkNodes(List<TemplateNode> nodes, Map<String, QueryParameterDeclaration> scope) {
        for (TemplateNode node : nodes) {
            switch (node) {
                case TextNode ignored -> {
                }
                case ValueNode value -> checkValue(value, scope);
                case FragmentNode fragment -> checkFragment(fragment);
                case IfNode ifNode -> checkIf(ifNode, scope);
                case EachNode each -> checkEach(each, scope);
                case JoinNode join -> checkNodes(join.getChildren(), scope);
                case PartNode part -> checkNodes(part.getChildren(), scope);
            }
        }
    }

    /**
     * 值占位不能是片段、对象或对象集合。
     *
     * @param node 值节点
     * @param scope 循环变量
     */
    private void checkValue(ValueNode node, Map<String, QueryParameterDeclaration> scope) {
        QueryParameterDeclaration resolved = resolve(node.getPath(), node.getPosition(), scope);
        if (resolved == null || resolved == ANY) {
            return;
        }
        String text = "#{" + node.getPath().text() + "}";
        if (resolved.getType() == QueryParameterType.FRAGMENT) {
            error(node.getPosition(), text + "：片段参数请用 @{" + node.getPath().text() + "} 输出");
        } else if (resolved.getType() == QueryParameterType.OBJECT) {
            error(node.getPosition(), text + "：对象不能直接写入 #{}，请写到具体字段");
        } else if (resolved.getType() == QueryParameterType.LIST
            && resolved.getElementType() == QueryParameterType.OBJECT) {
            error(node.getPosition(), text + "：对象集合不能直接写入 #{}，请用 {{#each}} 逐个取字段");
        }
    }

    /**
     * 片段占位必须引用 FRAGMENT 参数。
     *
     * @param node 片段节点
     */
    private void checkFragment(FragmentNode node) {
        QueryParameterDeclaration declaration = declarations.get(node.getName());
        if (declaration == null || declaration.getType() != QueryParameterType.FRAGMENT) {
            error(node.getPosition(), "@{" + node.getName() + "} 必须引用已声明的 FRAGMENT 参数");
            return;
        }
        used.add(node.getName());
    }

    /**
     * 检查各分支条件与内容。
     *
     * @param node 条件块
     * @param scope 循环变量
     */
    private void checkIf(IfNode node, Map<String, QueryParameterDeclaration> scope) {
        for (IfBranch branch : node.getBranches()) {
            if (branch.getCondition() != null) {
                kindOf(branch.getCondition(), scope);
            }
            checkNodes(branch.getChildren(), scope);
        }
    }

    /**
     * 循环必须遍历集合参数，变量名不能与参数或外层变量重名。
     *
     * @param node 循环块
     * @param scope 外层循环变量
     */
    private void checkEach(EachNode node, Map<String, QueryParameterDeclaration> scope) {
        QueryParameterDeclaration list = resolve(node.getPath(), node.getPosition(), scope);
        boolean isList = list != null && list != ANY && list.getType() == QueryParameterType.LIST;
        if (list != null && list != ANY && !isList) {
            error(node.getPosition(), "{{#each}} 只能遍历集合参数：" + node.getPath().text());
        }
        Map<String, QueryParameterDeclaration> inner = new HashMap<>(scope);
        defineVariable(node, node.getItemName(), isList ? list.elementDeclaration() : ANY, inner);
        if (node.getIndexName() != null) {
            defineVariable(node, node.getIndexName(), INDEX, inner);
        }
        checkNodes(node.getChildren(), inner);
    }

    /**
     * 定义循环变量；与参数或可见变量重名时报错。
     *
     * @param node 循环块
     * @param name 变量名
     * @param type 变量类型
     * @param scope 内层作用域
     */
    private void defineVariable(EachNode node, String name, QueryParameterDeclaration type,
        Map<String, QueryParameterDeclaration> scope) {
        if (declarations.containsKey(name) || scope.containsKey(name)) {
            error(node.getPosition(), "循环变量 " + name + " 与参数或外层循环变量重名");
        }
        scope.put(name, type);
    }

    /**
     * 解析路径的类型。
     *
     * @param path 路径
     * @param position 引用位置
     * @param scope 循环变量
     * @return 声明；类型未知时为 ANY；无法解析时记错并返回 null
     */
    private QueryParameterDeclaration resolve(TemplatePath path, SourcePosition position,
        Map<String, QueryParameterDeclaration> scope) {
        QueryParameterDeclaration current = root(path.root(), position, scope);
        String owner = path.root();
        for (String field : path.fields()) {
            if (current == null || current == ANY || current == CURSOR) {
                return current == null ? null : ANY;
            }
            current = field(current, owner, field, position);
            owner = owner + "." + field;
        }
        return current;
    }

    /**
     * 解析路径首段：系统参数、循环变量或参数。
     *
     * @param root 首段
     * @param position 引用位置
     * @param scope 循环变量
     * @return 声明；无法解析时记错并返回 null
     */
    private QueryParameterDeclaration root(String root, SourcePosition position,
        Map<String, QueryParameterDeclaration> scope) {
        if (SystemParameters.OPERATOR.equals(root)) {
            return OPERATOR;
        }
        if (SystemParameters.CURSOR.equals(root)) {
            cursorReferenced = true;
            return CURSOR;
        }
        if (root.startsWith("__")) {
            error(position, "未知的系统参数：" + root);
            return null;
        }
        if (scope.containsKey(root)) {
            return scope.get(root);
        }
        if (!declarations.containsKey(root)) {
            error(position, "引用了未声明的参数：" + root);
            return null;
        }
        used.add(root);
        return declarations.get(root);
    }

    /**
     * 取对象字段的声明。
     *
     * @param owner 所属声明
     * @param ownerText 所属路径文本
     * @param field 字段名
     * @param position 引用位置
     * @return 字段声明；无法解析时记错并返回 null
     */
    private QueryParameterDeclaration field(QueryParameterDeclaration owner, String ownerText, String field,
        SourcePosition position) {
        if (owner.getType() == QueryParameterType.LIST) {
            error(position, "集合参数 " + ownerText + " 不能直接取字段，请用 {{#each}}");
            return null;
        }
        QueryParameterDeclaration found = owner.getType() == QueryParameterType.OBJECT ? owner.field(field) : null;
        if (found == null) {
            error(position, ownerText + " 没有字段 " + field);
        }
        return found;
    }

    /**
     * 表达式的比较类别，同时检查比较运算。
     *
     * @param expression 表达式
     * @param scope 循环变量
     * @return 类别名
     */
    private String kindOf(TemplateExpression expression, Map<String, QueryParameterDeclaration> scope) {
        return switch (expression) {
            case PathExpression path -> pathKind(resolve(path.getPath(), path.getPosition(), scope));
            case LiteralExpression literal -> literal.getValue() == null ? NULL_KIND
                : ExpressionEvaluator.kind(literal.getValue());
            case NotExpression not -> {
                kindOf(not.getOperand(), scope);
                yield ExpressionEvaluator.BOOLEAN_KIND;
            }
            case LogicalExpression logical -> {
                kindOf(logical.getLeft(), scope);
                kindOf(logical.getRight(), scope);
                yield ExpressionEvaluator.BOOLEAN_KIND;
            }
            case ComparisonExpression comparison -> checkComparison(comparison, scope);
        };
    }

    /**
     * 路径解析结果的类别。
     *
     * @param declaration 声明、ANY 或 null
     * @return 类别名
     */
    private static String pathKind(QueryParameterDeclaration declaration) {
        if (declaration == null || declaration == ANY) {
            return ANY_KIND;
        }
        return ExpressionEvaluator.kindOf(declaration.getType());
    }

    /**
     * 检查比较两边的类型。
     *
     * @param comparison 比较表达式
     * @param scope 循环变量
     * @return 布尔类别
     */
    private String checkComparison(ComparisonExpression comparison, Map<String, QueryParameterDeclaration> scope) {
        String left = kindOf(comparison.getLeft(), scope);
        String right = kindOf(comparison.getRight(), scope);
        TemplateComparisonOperator operator = comparison.getOperator();
        SourcePosition position = comparison.getPosition();
        if (left.equals(ANY_KIND) || right.equals(ANY_KIND)) {
            return ExpressionEvaluator.BOOLEAN_KIND;
        }
        if (left.equals(NULL_KIND) || right.equals(NULL_KIND)) {
            if (operator.ordering()) {
                error(position, "null 只能用于 == 或 !=");
            }
        } else if (left.equals(ExpressionEvaluator.OTHER_KIND) || right.equals(ExpressionEvaluator.OTHER_KIND)) {
            error(position, "集合或对象不能参与比较");
        } else if (!left.equals(right)) {
            error(position, "比较两边类型不兼容：" + left + " " + operator.getCode() + " " + right);
        } else if (operator.ordering() && left.equals(ExpressionEvaluator.BOOLEAN_KIND)) {
            error(position, "布尔值不能比较大小");
        }
        return ExpressionEvaluator.BOOLEAN_KIND;
    }

    /**
     * 记录正文位置上的错误。
     *
     * @param position 位置
     * @param message 原因
     */
    private void error(SourcePosition position, String message) {
        problems.add(TemplateProblem.error(position, message));
    }
}
