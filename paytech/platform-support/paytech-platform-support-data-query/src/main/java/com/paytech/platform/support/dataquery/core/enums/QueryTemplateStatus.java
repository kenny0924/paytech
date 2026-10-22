package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模板状态（t_query_template.status）；只有 ACTIVE 可执行。 */
@Getter
@RequiredArgsConstructor
public enum QueryTemplateStatus implements CodeEnum {

    /** 可执行。 */
    ACTIVE("ACTIVE"),

    /** 已停用，执行返回业务拒绝。 */
    INACTIVE("INACTIVE");

    private final String code;
}
