package com.paytech.platform.support.accesscontrol.domain.operations;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账号启停的已校验当前事实。 */
@Getter
@AllArgsConstructor
public class OperationsAccountStateContext {
    /** 已经校验的原账号。 */
    private final OperationsAccount account;

    /** 当前 INTERNAL 成员原对象。 */
    private final OperationsMember member;

    /** 本次保存使用的微秒 UTC 时刻。 */
    private final Instant now;

}
