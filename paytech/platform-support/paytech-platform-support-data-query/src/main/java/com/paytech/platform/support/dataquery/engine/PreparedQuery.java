package com.paytech.platform.support.dataquery.engine;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 通过只读校验并完成分页改写、可直接执行的查询。 */
@Getter
@AllArgsConstructor
public final class PreparedQuery {

    /** 主查询 SQL，已追加 LIMIT rowLimit+1（OFFSET 方式另带 OFFSET）。 */
    private final String sql;

    /** 主查询绑定值，与 sql 中 ? 的顺序一致。 */
    private final List<Object> bindings;

    /** 计数 SQL：SELECT COUNT(*) FROM (去掉顶层 ORDER BY 的原句) t；不计数时为 null。 */
    private final String countSql;

    /** 计数绑定值；不计数时为 null。 */
    private final List<Object> countBindings;

    /** 最多返回的行数；实际多取一行判断 hasMore。 */
    private final int rowLimit;
}
