package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 出参列声明（columns_json 一项）：标题与屏蔽规则；列名不区分大小写匹配。 */
@Getter
@AllArgsConstructor
public final class QueryColumnRule {

    /** 结果列名。 */
    private final String name;

    /** 显示标题；为 null 时使用列名。 */
    private final String label;

    /** 屏蔽规则。 */
    private final ColumnMaskType mask;

    /** KEEP_EDGES 保留的开头字符数。 */
    private final int keepStart;

    /** KEEP_EDGES 保留的结尾字符数。 */
    private final int keepEnd;
}
