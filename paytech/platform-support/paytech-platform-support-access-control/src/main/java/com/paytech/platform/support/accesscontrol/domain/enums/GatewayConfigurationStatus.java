package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway 当前配置 Root 的启停状态。
 */
public enum GatewayConfigurationStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    DISABLED("DISABLED");

    private final String code;

    GatewayConfigurationStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
