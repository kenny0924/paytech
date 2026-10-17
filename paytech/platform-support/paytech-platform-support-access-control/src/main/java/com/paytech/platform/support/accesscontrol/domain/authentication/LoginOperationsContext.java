package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedTokens;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成关系校验的本库认证上下文；回滚后整体丢弃。 */
@Getter
@AllArgsConstructor
public class LoginOperationsContext {
    /** 本次提交使用的账号。 */
    private final OperationsAccount account;

    /** 当前凭据。 */
    private final OperationsPassword password;

    /** 当前成员，Bootstrap 为空。 */
    private final OperationsMember member;

    /** 完整的新会话。 */
    private final OperationsSession session;

    /** 事务外完成的签发材料。 */
    private final PreparedTokens tokens;

    /** 提交时点。 */
    private final Instant now;

}
