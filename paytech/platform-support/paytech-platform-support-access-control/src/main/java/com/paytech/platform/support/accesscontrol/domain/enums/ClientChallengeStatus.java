package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 一次性邮件挑战的持久化生命周期。 */
public enum ClientChallengeStatus implements CodeEnum {
    PENDING("PENDING"),

    CONSUMED("CONSUMED"),

    EXPIRED("EXPIRED"),

    SUPERSEDED("SUPERSEDED"),

    REVOKED("REVOKED");

    private final String code;

    /** 绑定数据库中的稳定业务代码。 */
    ClientChallengeStatus(String code) {
        this.code = code;
    }

    /** 返回持久化代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
