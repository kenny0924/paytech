package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Gateway 调用方需要据此区分认证拒绝和 Platform 技术不可用。 */
public enum ApiCredentialErrorCode implements CodeMessageEnum {
    API_CREDENTIAL_AUTHENTICATION_FAILED("API_CREDENTIAL_AUTHENTICATION_FAILED", "API Credential 无效"),

    API_CREDENTIAL_VALIDATION_UNAVAILABLE("API_CREDENTIAL_VALIDATION_UNAVAILABLE", "API Credential 校验暂时不可用");

    private final String code;
    private final String message;

    ApiCredentialErrorCode(String code, String message) {
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
