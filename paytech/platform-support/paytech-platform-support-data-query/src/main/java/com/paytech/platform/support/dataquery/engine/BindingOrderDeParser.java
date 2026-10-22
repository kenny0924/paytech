package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;

/**
 * 输出 SQL 时按实际输出顺序记录每个 ? 的原始序号，使改写后的绑定值与占位符一一对应；
 * 去掉顶层 ORDER BY 后其中的 ? 也随之从计数绑定中去掉。
 */
final class BindingOrderDeParser extends ExpressionDeParser {

    private final List<Integer> parameterIndexes = new ArrayList<>();

    /**
     * 记录占位符序号后按原样输出。
     *
     * @param parameter 占位符，序号由解析器按出现顺序从 1 编号
     * @param context 输出上下文
     * @param <S> 上下文类型
     * @return 输出缓冲
     */
    @Override
    public <S> StringBuilder visit(JdbcParameter parameter, S context) {
        parameterIndexes.add(parameter.getIndex());
        return super.visit(parameter, context);
    }

    /**
     * 输出顺序下的占位符原始序号。
     *
     * @return 序号，从 1 开始
     */
    List<Integer> parameterIndexes() {
        return parameterIndexes;
    }
}
