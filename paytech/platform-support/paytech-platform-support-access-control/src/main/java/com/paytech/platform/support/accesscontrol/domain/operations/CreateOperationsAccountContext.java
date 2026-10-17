package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 原子建立账号、凭据、成员及角色授权。 */
@Getter
@AllArgsConstructor
public class CreateOperationsAccountContext {
    /** 已经校验的原账号。 */
    private final OperationsAccount account;

    /** 当前 INTERNAL 成员原对象。 */
    private final OperationsMember member;

    /** 新账号初始密码凭据。 */
    private final OperationsPassword password;

    /** 已验证的初始授权角色。 */
    private final List<OperationsRole> roles;

    /** 授予人账号。 */
    private final long actorId;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
