package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 成员版本推进与角色替换的同一原子阶段。 */
@Getter
@AllArgsConstructor
public class AssignOperationsRolesContext {
    /** 已经校验的原账号。 */
    private final OperationsAccount account;

    /** 当前 INTERNAL 成员原对象。 */
    private final OperationsMember member;

    /** 已验证的完整角色集合。 */
    private final List<OperationsRole> roles;

    /** 授予人账号。 */
    private final long actorId;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
