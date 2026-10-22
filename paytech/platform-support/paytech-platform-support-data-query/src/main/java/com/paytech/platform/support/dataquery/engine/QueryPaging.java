package com.paytech.platform.support.dataquery.engine;

import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已按模板约束与系统上限确定的分页参数；改写时多取一行，只用来判断 hasMore。
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class QueryPaging {

    /** 分页方式。 */
    private final QueryPagingMode mode;

    /** 本次最多返回的行数：NONE 为 min(模板 maxRows, 系统上限)，OFFSET / CURSOR 为页大小。 */
    private final int rowLimit;

    /** OFFSET 方式的偏移行数；其他方式为 0。 */
    private final long offset;

    /** 是否另生成计数 SQL；只用于 OFFSET 且模板允许总数时。 */
    private final boolean withTotal;

    /**
     * 不分页。
     *
     * @param maxRows 最多返回的行数
     * @return 分页参数
     */
    public static QueryPaging none(int maxRows) {
        return new QueryPaging(QueryPagingMode.NONE, maxRows, 0, false);
    }

    /**
     * 偏移分页。
     *
     * @param size 页大小
     * @param offset 偏移行数，(page - 1) * size
     * @param withTotal 是否计数
     * @return 分页参数
     */
    public static QueryPaging offset(int size, long offset, boolean withTotal) {
        return new QueryPaging(QueryPagingMode.OFFSET, size, offset, withTotal);
    }

    /**
     * 游标分页；游标条件由模板自行写出。
     *
     * @param size 页大小
     * @return 分页参数
     */
    public static QueryPaging cursor(int size) {
        return new QueryPaging(QueryPagingMode.CURSOR, size, 0, false);
    }
}
