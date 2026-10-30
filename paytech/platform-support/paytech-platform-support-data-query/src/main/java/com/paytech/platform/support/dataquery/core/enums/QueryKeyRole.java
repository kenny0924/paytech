package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 结果列上键类型的角色；键类型的 default_role 只是编辑器默认值，模板列上的角色才是实际角色。 */
@Getter
@RequiredArgsConstructor
public enum QueryKeyRole implements CodeEnum {

    /** 链路键：非空值进入下一层展开。 */
    LINK("LINK"),

    /** 维度键：只展示，不自动展开，可由页面另起查询。 */
    DIMENSION("DIMENSION");

    private final String code;
}
