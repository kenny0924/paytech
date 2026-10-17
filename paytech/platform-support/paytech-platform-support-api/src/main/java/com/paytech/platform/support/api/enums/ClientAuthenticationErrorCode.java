package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Client 认证错误不能携带账号存在性或敏感值。 */
public enum ClientAuthenticationErrorCode implements CodeMessageEnum {
    CLIENT_AUTHENTICATION_FAILED("CLIENT_AUTHENTICATION_FAILED", "邮箱或密码无效"),

    CLIENT_SESSION_INVALID("CLIENT_SESSION_INVALID", "登录已失效，请重新登录"),

    CLIENT_EMAIL_LINK_INVALID("CLIENT_EMAIL_LINK_INVALID", "链接无效或已失效，请重新申请"),

    /** 成员不存在、不属于当前账号、未激活或工作区不可访问时统一返回，不区分具体原因。 */
    CLIENT_WORKSPACE_UNAVAILABLE("CLIENT_WORKSPACE_UNAVAILABLE", "工作区当前不可进入，请刷新列表后重试"),

    /** 当前工作区成员未被授予该操作所需权限。 */
    CLIENT_PERMISSION_DENIED("CLIENT_PERMISSION_DENIED", "没有执行该操作的权限");

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
