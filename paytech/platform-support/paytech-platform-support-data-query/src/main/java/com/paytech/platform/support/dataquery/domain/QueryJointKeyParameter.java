package com.paytech.platform.support.dataquery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 联合查询配置中的键参数：按哪个已声明参数、以哪种键类型触发本模板。 */
@Getter
@AllArgsConstructor
public final class QueryJointKeyParameter {

    /** 已声明的参数名。 */
    private final String name;

    /** 键类型编码。 */
    private final String keyType;
}
