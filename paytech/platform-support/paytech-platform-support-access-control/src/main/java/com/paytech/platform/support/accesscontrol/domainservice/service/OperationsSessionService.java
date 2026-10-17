package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;

/** 当前 Session、Account 与 Member 的实时授权前置能力。 */
public interface OperationsSessionService {

    /** 按当前数据库事实验证会话及所属身份，失败时拒绝访问。 */
    SessionContext requireCurrent(String accountId, String sessionId);

    /** 拒绝 Bootstrap 或尚未修改初始密码的受限上下文。 */
    void requireOrdinary(SessionContext context);

    /** 判断成员和所属 Tenant 是否都允许访问。 */
    boolean isActive(OperationsMember member);

    /** 将稳定正整数身份标识转换为内部 ID。 */
    long parseId(String value);

    /** 同一当前事实读取的账号、凭据、成员及会话。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    @lombok.experimental.Accessors(fluent = true)
    class SessionContext {
        /** 当前账号。 */
        private final OperationsAccount account;
        /** 当前密码凭据。 */
        private final OperationsPassword password;
        /** 当前成员，Bootstrap 为空。 */
        private final OperationsMember member;
        /** 当前认证会话。 */
        private final OperationsSession session;
    }
}
