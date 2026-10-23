package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 应用状态（t_i18n_application.status）；停用的应用不提供 manifest。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStatus implements CodeEnum {

    /** 启用。 */
    ENABLED("ENABLED"),

    /** 停用；本期没有停用入口，只由运维数据维护。 */
    DISABLED("DISABLED");

    private final String code;
}
