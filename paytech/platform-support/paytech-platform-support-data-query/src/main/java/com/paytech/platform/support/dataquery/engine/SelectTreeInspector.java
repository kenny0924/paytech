package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * 遍历整棵 SELECT 语法树（含子查询、CTE、表达式），收集表引用、函数调用、各层 PlainSelect 与 CTE 名称。
 *
 * <p>复用 JSqlParser 的 TablesNamesFinder 完成遍历，只在访问回调中收集节点，不改变其行为。</p>
 */
final class SelectTreeInspector extends TablesNamesFinder<Void> {

    private final List<Table> tables = new ArrayList<>();

    private final List<Function> functions = new ArrayList<>();

    private final List<PlainSelect> plainSelects = new ArrayList<>();

    private final Set<String> cteNames = new HashSet<>();

    /**
     * 收集表引用。
     *
     * @param table 表
     * @param context 遍历上下文
     * @param <S> 上下文类型
     * @return 父类结果
     */
    @Override
    public <S> Void visit(Table table, S context) {
        tables.add(table);
        return super.visit(table, context);
    }

    /**
     * 收集函数调用。
     *
     * @param function 函数
     * @param context 遍历上下文
     * @param <S> 上下文类型
     * @return 父类结果
     */
    @Override
    public <S> Void visit(Function function, S context) {
        functions.add(function);
        return super.visit(function, context);
    }

    /**
     * 收集各层 PlainSelect，用于检查 INTO 与 FOR UPDATE。
     *
     * @param plainSelect 查询
     * @param context 遍历上下文
     * @param <S> 上下文类型
     * @return 父类结果
     */
    @Override
    public <S> Void visit(PlainSelect plainSelect, S context) {
        plainSelects.add(plainSelect);
        return super.visit(plainSelect, context);
    }

    /**
     * 收集 CTE 名称，引用 CTE 时不按表处理。
     *
     * @param withItem CTE
     * @param context 遍历上下文
     * @param <S> 上下文类型
     * @return 父类结果
     */
    @Override
    public <S> Void visit(WithItem<?> withItem, S context) {
        if (withItem.getAlias() != null) {
            cteNames.add(ReadOnlySqlGuard.unquote(withItem.getAlias().getName()).toLowerCase(Locale.ROOT));
        }
        return super.visit(withItem, context);
    }

    /**
     * 收集到的表引用。
     *
     * @return 表
     */
    List<Table> tables() {
        return tables;
    }

    /**
     * 收集到的函数调用。
     *
     * @return 函数
     */
    List<Function> functions() {
        return functions;
    }

    /**
     * 收集到的 PlainSelect。
     *
     * @return 查询
     */
    List<PlainSelect> plainSelects() {
        return plainSelects;
    }

    /**
     * 是否为 CTE 名称。
     *
     * @param name 表名，已去掉引号
     * @return true 表示引用的是 CTE
     */
    boolean isCte(String name) {
        return cteNames.contains(name.toLowerCase(Locale.ROOT));
    }
}
