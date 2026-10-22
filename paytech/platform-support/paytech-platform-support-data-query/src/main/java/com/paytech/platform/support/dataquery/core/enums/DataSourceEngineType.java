package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据源引擎类型；决定执行引擎与 SQL 方言。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceEngineType implements CodeEnum {

    /** MySQL，包括只读从库。 */
    MYSQL("MYSQL"),

    /** Apache Doris，通过 MySQL 协议访问。 */
    DORIS("DORIS");

    private final String code;
}
