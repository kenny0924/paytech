package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 当前账号与会话的公开查询投影，不包含凭据或数据库对象。 */
@Getter
@Accessors(fluent = true)
public final class ClientAccountView {
    /** 账号编号。 */ private final long accountId;
    /** 会话编号。 */ private final long sessionId;
    /** 脱敏邮箱。 */ private final String emailMasked;
    /** 邮箱验证状态。 */ private final boolean emailVerified;
    /** 上下文代码。 */ private final String contextType;
    /** 租户编号；账号上下文为空。 */ private final Long tenantId;
    /** 成员编号；账号上下文为空。 */ private final Long memberId;
    /** 不可变的允许动作集合。 */ private final List<String> allowedActions;
    /** 刷新时工作区失效而降级为账号上下文的公开原因码；其他情况为空。 */ private final String fallbackReason;

    /** 固定账号与会话的查询投影，复制允许动作集合；无降级原因。 */
    public ClientAccountView(long accountId, long sessionId, String emailMasked, boolean emailVerified,
        String contextType, Long tenantId, Long memberId, List<String> allowedActions) {
        this(accountId, sessionId, emailMasked, emailVerified, contextType, tenantId, memberId, allowedActions, null);
    }

    /** 固定账号与会话的查询投影，复制允许动作集合并携带降级原因。 */
    public ClientAccountView(long accountId, long sessionId, String emailMasked, boolean emailVerified,
        String contextType, Long tenantId, Long memberId, List<String> allowedActions, String fallbackReason) {
        this.accountId = accountId; this.sessionId = sessionId; this.emailMasked = emailMasked;
        this.emailVerified = emailVerified; this.contextType = contextType; this.tenantId = tenantId;
        this.memberId = memberId; this.allowedActions = List.copyOf(allowedActions);
        this.fallbackReason = fallbackReason;
    }
}
