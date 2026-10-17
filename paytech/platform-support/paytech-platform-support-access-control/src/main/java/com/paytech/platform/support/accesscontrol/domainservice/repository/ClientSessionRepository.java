package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;

/** ClientSession 单表持久化合同；更新成功后同步传入对象。 */
public interface ClientSessionRepository {
    /** 读取指定 CLIENT 会话。 */
    ClientSession session(long sessionId);

    /** 锁定 CLIENT 会话并刷新查询缓存。 */
    ClientSession lockSession(long sessionId);

    /** 保存新的 CLIENT 账号或租户上下文会话。 */
    void insertSession(Issue issue);

    /** 按原版本推进会话当前刷新代与空闲期限。 */
    void advanceSession(ClientSession row, Issue issue);

    /** 终止活动 CLIENT 会话，已结束会话保持原终态。 */
    void revokeSession(ClientSession row, OperationsSessionStatus status, String reason, Instant now);

    /** 密码重置时撤销该 CUSTOMER 账号全部 Channel 的会话。 */
    void revokeAccountSessions(long accountId, Instant now);

}
