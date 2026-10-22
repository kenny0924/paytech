package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryCursorCodec;

/**
 * 按列声明屏蔽结果并生成游标；纯 Java，与引擎无关。
 * <li>列名不区分大小写匹配；没有声明的列原样返回，标题使用列名。</li>
 * <li>游标取屏蔽前的值；游标列必须出现在结果中。</li>
 */
public final class QueryResultShaper {

    private static final char MASK = '*';

    private QueryResultShaper() {
    }

    /**
     * 下一页游标；没有更多行或不是游标分页时为 null。
     *
     * @param result 原始结果
     * @param cursorColumns 游标列；非游标分页为空
     * @param versionNo 模板版本号
     * @return 游标
     */
    public static String nextCursor(QueryResult result, List<String> cursorColumns, int versionNo) {
        if (!result.isHasMore() || cursorColumns.isEmpty() || result.getRows().isEmpty()) {
            return null;
        }
        List<Object> last = result.getRows().getLast();
        Map<String, Object> values = new LinkedHashMap<>();
        for (String column : cursorColumns) {
            int index = indexOf(result.getColumns(), column);
            if (index < 0) {
                throw new BizCheckFailException("游标列不在查询结果中: " + column);
            }
            values.put(column, last.get(index));
        }
        return QueryCursorCodec.encode(versionNo, values);
    }

    /**
     * 每个结果列的规则；没有声明时为 null。
     *
     * @param columns 结果列名
     * @param rules 列声明
     * @return 与 columns 对齐的规则
     */
    public static List<QueryColumnRule> rulesFor(List<String> columns, List<QueryColumnRule> rules) {
        Map<String, QueryColumnRule> byName = new LinkedHashMap<>();
        rules.forEach(rule -> byName.put(rule.getName().toLowerCase(Locale.ROOT), rule));
        List<QueryColumnRule> aligned = new ArrayList<>(columns.size());
        columns.forEach(column -> aligned.add(byName.get(column.toLowerCase(Locale.ROOT))));
        return aligned;
    }

    /**
     * 屏蔽一行：去掉 HIDE 列，其余按规则替换。
     *
     * @param row 原始行
     * @param rules 与列对齐的规则
     * @return 屏蔽后的行
     */
    public static List<Object> maskRow(List<Object> row, List<QueryColumnRule> rules) {
        List<Object> masked = new ArrayList<>(row.size());
        for (int index = 0; index < row.size(); index++) {
            QueryColumnRule rule = rules.get(index);
            if (rule != null && rule.getMask() == ColumnMaskType.HIDE) {
                continue;
            }
            masked.add(rule == null ? row.get(index) : mask(row.get(index), rule));
        }
        return masked;
    }

    /**
     * 按规则屏蔽一个值；null 不屏蔽。
     *
     * @param value 值
     * @param rule 规则
     * @return 屏蔽后的值
     */
    static Object mask(Object value, QueryColumnRule rule) {
        if (value == null || rule.getMask() == ColumnMaskType.NONE) {
            return value;
        }
        String text = String.valueOf(value);
        if (rule.getMask() == ColumnMaskType.FULL) {
            return String.valueOf(MASK).repeat(text.length());
        }
        int start = Math.min(rule.getKeepStart(), text.length());
        int end = Math.min(rule.getKeepEnd(), text.length() - start);
        return text.substring(0, start) + String.valueOf(MASK).repeat(text.length() - start - end)
            + text.substring(text.length() - end);
    }

    /** 不区分大小写查找列序号；没有时为 -1。 */
    static int indexOf(List<String> columns, String column) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).equalsIgnoreCase(column)) {
                return index;
            }
        }
        return -1;
    }
}
