package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 租户成员邀请的持久化状态
 * <li>PENDING 占用活动位；到期不改状态，读取时推导为 EXPIRED，同邮箱再次邀请时才写为 EXPIRED。</li>
 * <li>SUPERSEDED 为模型保留值，本期不写入。</li>
 */
public enum TenantInvitationStatus implements CodeEnum {
    PENDING("PENDING"),

    ACCEPTED("ACCEPTED"),

    EXPIRED("EXPIRED"),

    REVOKED("REVOKED"),

    SUPERSEDED("SUPERSEDED");

    private final String code;

    /** 绑定数据库中的稳定业务代码。 */
    TenantInvitationStatus(String code) {
        this.code = code;
    }

    /** 返回持久化代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
