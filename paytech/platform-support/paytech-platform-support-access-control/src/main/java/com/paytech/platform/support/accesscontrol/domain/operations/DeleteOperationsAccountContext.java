package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 关闭账号、成员、角色和会话的本地原子上下文。 */
@Getter
@AllArgsConstructor
public class DeleteOperationsAccountContext {
    /** 已经校验的原账号。 */
    private final OperationsAccount account;

    /** 当前 INTERNAL 成员原对象。 */
    private final OperationsMember member;

    /** 待撤销凭据；缺失历史凭据时可空。 */
    private final OperationsPassword password;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
