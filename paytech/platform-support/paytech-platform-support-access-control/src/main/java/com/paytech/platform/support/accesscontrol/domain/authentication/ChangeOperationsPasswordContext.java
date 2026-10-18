package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成关系校验的本库认证上下文；回滚后整体丢弃。 */
@Getter
@AllArgsConstructor
public class ChangeOperationsPasswordContext {
    /** 当前账号。 */
    private final OperationsAccount account;

    /** 当前凭据。 */
    private final OperationsPassword password;

    /** 本次认证会话。 */
    private final OperationsSession session;

    /** 新密码 PHC，禁止日志。 */
    private final String nextVerifier;

    /** 提交时点。 */
    private final Instant now;

}
