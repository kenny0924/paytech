package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client API Credential 的封闭生命周期状态。 */
@Getter
@AllArgsConstructor
public enum ApiCredentialStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    REVOKED("REVOKED"),

    EXPIRED("EXPIRED");

    private final String code;
}
