package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.PreparedTokens;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成关系校验的本库认证上下文；回滚后整体丢弃。 */
@Getter
@AllArgsConstructor
public class RefreshOperationsContext {
    /** 当前账号，可在安全失效场景中缺失。 */
    private final OperationsAccount account;

    /** 当前凭据，可在安全失效场景中缺失。 */
    private final OperationsPassword password;

    /** 当前成员，Bootstrap 为空。 */
    private final OperationsMember member;

    /** 当前锁定会话。 */
    private final OperationsSession session;

    /** 已经证明持有的原刷新代。 */
    private final OperationsRefresh refresh;

    /** 候选后继；只在轮换分支要求存在。 */
    private final PreparedTokens tokens;

    /** 提交时点。 */
    private final Instant now;

}
