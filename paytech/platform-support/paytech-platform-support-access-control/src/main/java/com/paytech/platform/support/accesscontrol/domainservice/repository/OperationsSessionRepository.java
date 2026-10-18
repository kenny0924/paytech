package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;

/** Operations 会话事实访问。 */
public interface OperationsSessionRepository {
    /** 读取受 OPERATIONS Realm/Channel 限定的会话。 */
    OperationsSession session(long id);

    /** 刷新及退出以会话行锁为数据库仲裁点。 */
    OperationsSession lockSession(long id);

    /** 保存已完整构造的会话。 */
    void insert(OperationsSession session);

    /** 条件推进代号及空闲期限，不延长绝对期限。 */
    void advance(OperationsSession session, Instant now);

    /** 只撤销活动会话，保留原终态。 */
    void revoke(OperationsSession session, OperationsSessionStatus status, String reason, Instant now);

    /** 改密时确认当前会话仍匹配原认证快照。 */
    void revokeCurrent(OperationsSession session, Instant now);

    /** 撤销账号所有活动会话。 */
    void revokeAccount(long accountId, String reason, Instant now);

    /** 撤销账号全部活动会话，并同步本次上下文已持有的会话。 */
    void revokeAccount(OperationsSession currentSession, String reason, Instant now);

}
