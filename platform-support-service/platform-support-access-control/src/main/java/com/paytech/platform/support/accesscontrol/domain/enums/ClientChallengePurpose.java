package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client 本期允许的一次性邮件挑战用途。 */
public enum ClientChallengePurpose implements CodeEnum {
    SIGN_UP_EMAIL("SIGN_UP_EMAIL"),

    PASSWORD_RESET("PASSWORD_RESET");

    private final String code;

    /** 绑定数据库中的稳定业务代码。 */
    ClientChallengePurpose(String code) {
        this.code = code;
    }

    /** 返回持久化代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
