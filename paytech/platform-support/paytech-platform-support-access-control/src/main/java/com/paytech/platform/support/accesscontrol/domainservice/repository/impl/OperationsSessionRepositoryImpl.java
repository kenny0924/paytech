package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSessionRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsSessionRepositoryImpl implements OperationsSessionRepository {
    private final OperationsIdentityMapper mapper;
    /** 读取受 OPERATIONS Realm/Channel 限定的会话。 */
    @Override
    public OperationsSession session(long id) {
        return mapper.session(id);
    }

    /** 刷新及退出以会话行锁为数据库仲裁点。 */
    @Override
    public OperationsSession lockSession(long id) {
        return mapper.lockSession(id);
    }

    /** 保存已完整构造的会话。 */
    @Override
    public void insert(OperationsSession session) {
        requireOne(mapper.insertSession(session));
    }

    /** 条件推进代号及空闲期限，不延长绝对期限。 */
    @Override
    public void advance(OperationsSession session, Instant now) {
        Instant idle = now.plusSeconds(1800).isBefore(session.absoluteExpireTime()) ? now.plusSeconds(1800) : session.absoluteExpireTime();
        requireOne(mapper.advanceSession(session.id(), session.generationNo(), session.generationNo() + 1,
            session.accountEpoch(), session.credentialVersion(), now, idle));
        session.generationNo(session.generationNo() + 1);
        session.lastActivityTime(now);
        session.idleExpireTime(idle);
    }

    /** 只撤销活动会话，保留原终态。 */
    @Override
    public void revoke(OperationsSession session, OperationsSessionStatus status, String reason, Instant now) {
        if (session.status() != OperationsSessionStatus.ACTIVE) { return; }
        requireOne(mapper.revokeSession(session.id(), status.getCode(), reason, now));
        session.status(status);
    }

    /** 改密时确认当前会话仍匹配原认证快照。 */
    @Override
    public void revokeCurrent(OperationsSession session, Instant now) {
        requireOne(mapper.revokeCurrentSession(session.id(), session.accountId(), session.accountEpoch(),
            session.credentialVersion(), "REVOKED", "PASSWORD_CHANGED", now));
        session.status(OperationsSessionStatus.REVOKED);
    }

    /** 撤销账号所有活动会话。 */
    @Override
    public void revokeAccount(long accountId, String reason, Instant now) {
        mapper.revokeAccountSessions(accountId, "REVOKED", reason, now);
    }

    /** 批量写入成功后同步上下文原对象，保留已存在的终态。 */
    @Override
    public void revokeAccount(OperationsSession currentSession, String reason, Instant now) {
        revokeAccount(currentSession.accountId(), reason, now);
        if (currentSession.status() == OperationsSessionStatus.ACTIVE) {
            currentSession.status(OperationsSessionStatus.REVOKED);
        }
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }
}
