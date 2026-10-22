package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行与试运行的最终结果：已屏蔽、已生成游标；试运行另附实际执行的 SQL 与绑定值。 */
@Getter
@AllArgsConstructor
public final class DataQueryResult {

    /** 执行的模板版本号；试运行为 0。 */
    private final int versionNo;

    /** 结果列（已去掉 HIDE 列）。 */
    private final List<Column> columns;

    /** 数据行，与 columns 对齐。 */
    private final List<List<Object>> rows;

    /** 是否还有更多行。 */
    private final boolean hasMore;

    /** 总数；未计数时为 null。 */
    private final Long total;

    /** 下一页游标；没有时为 null。 */
    private final String nextCursor;

    /** 数据库执行耗时毫秒。 */
    private final long elapsedMillis;

    /** 主查询 SQL（带 ?，已追加分页）。 */
    private final String sql;

    /** 主查询绑定值的显示文本。 */
    private final List<String> bindings;

    /** 计数 SQL；未计数时为 null。 */
    private final String countSql;

    /** 结果列。 */
    @Getter
    @AllArgsConstructor
    public static final class Column {

        /** 列名。 */
        private final String name;

        /** 显示标题。 */
        private final String label;
    }
}
