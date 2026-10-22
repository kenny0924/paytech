package com.paytech.platform.support.dataquery.engine;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 引擎返回的结果：已转换值类型、未屏蔽，已去掉判断 hasMore 的多取行。 */
@Getter
@AllArgsConstructor
public final class QueryResult {

    /** 结果列名（列标签），按查询列顺序。 */
    private final List<String> columns;

    /** 数据行，每行与 columns 对齐；值为 String、Boolean 或 null。 */
    private final List<List<Object>> rows;

    /** 是否还有更多行。 */
    private final boolean hasMore;

    /** 总数；未计数时为 null。 */
    private final Long total;

    /** 数据库执行耗时毫秒（含计数）。 */
    private final long elapsedMillis;
}
