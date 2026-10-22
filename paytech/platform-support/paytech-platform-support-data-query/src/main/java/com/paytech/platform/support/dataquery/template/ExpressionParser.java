package com.paytech.platform.support.dataquery.template;

import java.math.BigDecimal;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.dataquery.core.enums.TemplateComparisonOperator;
import com.paytech.platform.support.dataquery.core.enums.TemplateLogicalOperator;

/**
 * 条件表达式的递归下降解析：or := and ("||" and)*，and := unary ("&&" unary)*，
 * unary := "!" unary | cmp，cmp := primary (op primary)?，primary := path | 字面量 | "(" or ")"。
 */
final class ExpressionParser {

    private final TagArguments arguments;

    /**
     * 以标签参数读取器构造。
     *
     * @param arguments 读取器
     */
    ExpressionParser(TagArguments arguments) {
        this.arguments = arguments;
    }

    /**
     * 解析完整表达式，要求读完全部单元。
     *
     * @return 表达式
     */
    TemplateExpression parseAll() {
        TemplateExpression expression = parseOr();
        arguments.expectEnd();
        return expression;
    }

    /**
     * or 层。
     *
     * @return 表达式
     */
    private TemplateExpression parseOr() {
        TemplateExpression left = parseAnd();
        while (arguments.peek() != null && arguments.peek().isSymbol(TemplateLogicalOperator.OR.getCode())) {
            arguments.next("");
            left = new LogicalExpression(left.getPosition(), TemplateLogicalOperator.OR, left, parseAnd());
        }
        return left;
    }

    /**
     * and 层。
     *
     * @return 表达式
     */
    private TemplateExpression parseAnd() {
        TemplateExpression left = parseUnary();
        while (arguments.peek() != null && arguments.peek().isSymbol(TemplateLogicalOperator.AND.getCode())) {
            arguments.next("");
            left = new LogicalExpression(left.getPosition(), TemplateLogicalOperator.AND, left, parseUnary());
        }
        return left;
    }

    /**
     * 一元非层。
     *
     * @return 表达式
     */
    private TemplateExpression parseUnary() {
        TagToken token = arguments.peek();
        if (token != null && token.isSymbol("!")) {
            arguments.next("");
            return new NotExpression(token.getPosition(), parseUnary());
        }
        return parseComparison();
    }

    /**
     * 比较层；最多一个比较运算符。
     *
     * @return 表达式
     */
    private TemplateExpression parseComparison() {
        TemplateExpression left = parsePrimary();
        TagToken token = arguments.peek();
        TemplateComparisonOperator operator = token == null || token.getKind() != TagToken.Kind.SYMBOL ? null
            : EnumUtils.getEnumByCode(TemplateComparisonOperator.class, token.getText());
        if (operator == null) {
            return left;
        }
        arguments.next("");
        return new ComparisonExpression(token.getPosition(), operator, left, parsePrimary());
    }

    /**
     * 基本项：路径、字符串、数字、true、false、null 或括号。
     *
     * @return 表达式
     */
    private TemplateExpression parsePrimary() {
        TagToken token = arguments.next("条件");
        return switch (token.getKind()) {
            case STRING -> new LiteralExpression(token.getPosition(), token.getText());
            case NUMBER -> new LiteralExpression(token.getPosition(), number(token));
            case WORD -> word(token);
            case SYMBOL -> parenthesized(token);
        };
    }

    /**
     * 括号表达式。
     *
     * @param token 已读取的符号
     * @return 括号内的表达式
     */
    private TemplateExpression parenthesized(TagToken token) {
        if (!token.isSymbol("(")) {
            throw arguments.unexpected(token, "路径或字面量");
        }
        TemplateExpression inner = parseOr();
        arguments.expectSymbol(")");
        return inner;
    }

    /**
     * 单词：关键字字面量或路径。
     *
     * @param token 单词
     * @return 字面量或路径表达式
     */
    private TemplateExpression word(TagToken token) {
        return switch (token.getText()) {
            case "true" -> new LiteralExpression(token.getPosition(), Boolean.TRUE);
            case "false" -> new LiteralExpression(token.getPosition(), Boolean.FALSE);
            case "null" -> new LiteralExpression(token.getPosition(), null);
            default -> new PathExpression(token.getPosition(), path(token));
        };
    }

    /**
     * 把单词解析为路径。
     *
     * @param token 单词
     * @return 路径
     */
    static TemplatePath path(TagToken token) {
        TemplatePath path = TemplatePath.parse(token.getText());
        if (path == null) {
            throw new TemplateCheckException(token.getPosition(), "路径不合法：" + token.getText());
        }
        return path;
    }

    /**
     * 把数字单元解析为 BigDecimal。
     *
     * @param token 数字
     * @return 数值
     */
    private static BigDecimal number(TagToken token) {
        if (!token.getText().matches("-?\\d+(\\.\\d+)?")) {
            throw new TemplateCheckException(token.getPosition(), "数字不合法：" + token.getText());
        }
        return new BigDecimal(token.getText());
    }
}
