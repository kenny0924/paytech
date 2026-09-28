package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Gateway 配置管理调用方需要分支处理的稳定错误。 */
public enum GatewayConfigurationErrorCode implements CodeMessageEnum {
    GATEWAY_CONFIGURATION_NOT_FOUND("GATEWAY_CONFIGURATION_NOT_FOUND", "Gateway 配置不存在");

    private final String code;
    private final String message;

    GatewayConfigurationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
