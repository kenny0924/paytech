package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联合查询配置中的结果列标注；列名与结果列不区分大小写匹配。 */
@Getter
@AllArgsConstructor
public final class QueryJointColumn {

    /** 结果列名。 */
    private final String name;

    /** 键类型编码。 */
    private final String keyType;

    /** 实际角色：LINK 进入下一层，DIMENSION 只展示。 */
    private final QueryKeyRole keyRole;
}
