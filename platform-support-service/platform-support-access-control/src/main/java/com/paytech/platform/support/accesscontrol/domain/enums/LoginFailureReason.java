package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 仅用于内部审计的登录拒绝原因，不改变对外通用认证错误。 */
@Getter
@AllArgsConstructor
public enum LoginFailureReason implements CodeEnum {
    /** 规范化标识未定位到当前 Realm 的账号。 */
    UNKNOWN_ACCOUNT("UNKNOWN_ACCOUNT"),

    /** 账号生命周期当前不允许登录。 */
    ACCOUNT_DISABLED("ACCOUNT_DISABLED"),

    /** 连续密码错误引起的临时锁定尚未到期。 */
    ACCOUNT_LOCKED("ACCOUNT_LOCKED"),

    /** 账号没有当前有效的密码凭据。 */
    CREDENTIAL_UNAVAILABLE("CREDENTIAL_UNAVAILABLE"),

    /** 输入密码与当前有效凭据不匹配。 */
    PASSWORD_MISMATCH("PASSWORD_MISMATCH"),

    /** 成员关系或所属 Tenant 不存在、越界或不可访问。 */
    MEMBER_TENANT_UNAVAILABLE("MEMBER_TENANT_UNAVAILABLE");

    private final String code;
}
