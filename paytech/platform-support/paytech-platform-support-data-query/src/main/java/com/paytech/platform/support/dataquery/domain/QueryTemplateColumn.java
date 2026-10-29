package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板签名中的一个出参列声明。 */
@Getter
@AllArgsConstructor
public final class QueryTemplateColumn {

    /** 列名，与结果列不区分大小写匹配。 */
    private final String name;

    /** 是否配置了屏蔽规则（mask 不是 NONE，含 HIDE）；屏蔽后的值不能用于判断与分支。 */
    private final boolean masked;
}
