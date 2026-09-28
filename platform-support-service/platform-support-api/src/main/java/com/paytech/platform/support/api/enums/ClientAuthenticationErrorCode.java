package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Client 认证错误不能携带账号存在性或敏感值。 */
public enum ClientAuthenticationErrorCode implements CodeMessageEnum {
    CLIENT_AUTHENTICATION_FAILED("CLIENT_AUTHENTICATION_FAILED", "邮箱或密码无效"),

    CLIENT_SESSION_INVALID("CLIENT_SESSION_INVALID", "登录已失效，请重新登录"),

    CLIENT_EMAIL_LINK_INVALID("CLIENT_EMAIL_LINK_INVALID", "链接无效或已失效，请重新申请");

    private final String code;
    private final String message;
    /** 绑定稳定错误码和安全提示。 */
    ClientAuthenticationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    /** 返回稳定错误码。 */
    @Override public String getCode() { return code; }
    /** 返回不包含敏感信息的提示。 */
    @Override public String getMessage() { return message; }
}
