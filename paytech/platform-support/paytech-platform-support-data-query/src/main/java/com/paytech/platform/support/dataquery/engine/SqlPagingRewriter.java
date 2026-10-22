package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

/**
 * 在语法树顶层追加分页子句并生成计数 SQL；改写后按占位符实际输出顺序重排绑定值。
 *
 * <p>NONE、CURSOR：LIMIT rowLimit+1；OFFSET：LIMIT rowLimit+1 OFFSET n；
 * 计数：SELECT COUNT(*) FROM (去掉顶层 ORDER BY 与分页的原句) t。传入的语法树会被修改。</p>
 */
public final class SqlPagingRewriter {

    /**
     * 改写查询。
     *
     * @param select 已通过只读校验的语法树，顶层没有 LIMIT
     * @param bindings 渲染得到的绑定值，按原 SQL 中 ? 的顺序
     * @param paging 分页参数
     * @return 可执行的查询
     */
    public PreparedQuery rewrite(Select select, List<Object> bindings, QueryPaging paging) {
        select.setLimit(new Limit().withRowCount(new LongValue(paging.getRowLimit() + 1L)));
        if (paging.getMode() == QueryPagingMode.OFFSET) {
            select.setOffset(new Offset().withOffset(new LongValue(paging.getOffset())));
        }
        List<Object> mainBindings = new ArrayList<>();
        String sql = deparse(select, bindings, mainBindings);
        if (mainBindings.size() != bindings.size()) {
            throw new ErrorException("分页改写后占位符数与绑定值数不一致：" + mainBindings.size() + " / " + bindings.size());
        }
        if (!paging.isWithTotal()) {
            return new PreparedQuery(sql, Collections.unmodifiableList(mainBindings), null, null, paging.getRowLimit());
        }
        select.setLimit(null);
        select.setOffset(null);
        select.setOrderByElements(null);
        List<Object> countBindings = new ArrayList<>();
        String countSql = "SELECT COUNT(*) FROM (" + deparse(select, bindings, countBindings) + ") t";
        return new PreparedQuery(sql, Collections.unmodifiableList(mainBindings), countSql,
            Collections.unmodifiableList(countBindings), paging.getRowLimit());
    }

    /**
     * 输出 SQL，并按输出顺序收集绑定值。
     *
     * @param select 语法树
     * @param bindings 原绑定值
     * @param ordered 收集输出顺序下的绑定值
     * @return SQL
     */
    private static String deparse(Select select, List<Object> bindings, List<Object> ordered) {
        StringBuilder buffer = new StringBuilder();
        BindingOrderDeParser expressionDeParser = new BindingOrderDeParser();
        SelectDeParser selectDeParser = new SelectDeParser(expressionDeParser, buffer);
        expressionDeParser.setSelectVisitor(selectDeParser);
        expressionDeParser.setBuilder(buffer);
        select.accept((SelectVisitor<StringBuilder>) selectDeParser, null);
        for (Integer index : expressionDeParser.parameterIndexes()) {
            if (index == null || index < 1 || index > bindings.size()) {
                throw new ErrorException("占位符序号超出绑定值范围：" + index);
            }
            ordered.add(bindings.get(index - 1));
        }
        return buffer.toString();
    }
}
