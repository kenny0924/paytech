package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成关系校验的本库认证上下文；回滚后整体丢弃。 */
@Getter
@AllArgsConstructor
public class FirstOperationsAdministratorContext {
    /** 已经独占锁定的 Bootstrap。 */
    private final OperationsAccount bootstrap;

    /** 即将撤销的 Bootstrap 密码。 */
    private final OperationsPassword bootstrapPassword;

    /** 本次认证会话。 */
    private final OperationsSession bootstrapSession;

    /** 待创建的首个管理员。 */
    private final OperationsAccount account;

    /** 待创建的初始密码。 */
    private final OperationsPassword password;

    /** 待创建的固定 INTERNAL 成员。 */
    private final OperationsMember member;

    /** 批准的 PLATFORM_ADMIN 系统角色。 */
    private final OperationsRole role;

    /** 本次新授权的唯一标识。 */
    private final long grantId;

    /** 提交时点。 */
    private final Instant now;

}
