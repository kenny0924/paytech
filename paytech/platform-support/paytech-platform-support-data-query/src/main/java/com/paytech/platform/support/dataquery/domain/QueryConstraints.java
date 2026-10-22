package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板约束（constraints_json）；保存时已校验不超过系统硬上限。 */
@Getter
@AllArgsConstructor
public final class QueryConstraints {

    /** 最多返回的行数。 */
    private final int maxRows;

    /** 单次查询超时秒数。 */
    private final int timeoutSeconds;

    /** 分页方式。 */
    private final QueryPagingMode pagingMode;

    /** 页大小上限；NONE 时为 null。 */
    private final Integer maxPageSize;

    /** 是否允许返回总数（只对 OFFSET）。 */
    private final boolean countAllowed;

    /** 游标列；非 CURSOR 时为空列表。 */
    private final List<String> cursorColumns;
}
