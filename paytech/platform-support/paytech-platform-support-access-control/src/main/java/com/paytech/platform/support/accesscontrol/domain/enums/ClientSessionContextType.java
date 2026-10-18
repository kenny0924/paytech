package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CLIENT 会话的账号级与租户级上下文，不承载 Operations Bootstrap。 */
public enum ClientSessionContextType implements CodeEnum {
    ACCOUNT("ACCOUNT"),

    TENANT("TENANT");

    private final String code;
    /** 绑定持久化代码。 */
    ClientSessionContextType(String code) { this.code = code; }
    /** 返回稳定协议代码。 */
    @Override public String getCode() { return code; }
}
