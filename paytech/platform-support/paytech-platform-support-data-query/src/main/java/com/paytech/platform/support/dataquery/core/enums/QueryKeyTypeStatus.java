package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 键类型状态（t_query_key_type.status）；被 ACTIVE 模板当前版本引用时不能停用。 */
@Getter
@RequiredArgsConstructor
public enum QueryKeyTypeStatus implements CodeEnum {

    /** 可被模板引用、可作为联合查询的键。 */
    ACTIVE("ACTIVE"),

    /** 已停用，模板保存与执行都不再接受。 */
    INACTIVE("INACTIVE");

    private final String code;
}
