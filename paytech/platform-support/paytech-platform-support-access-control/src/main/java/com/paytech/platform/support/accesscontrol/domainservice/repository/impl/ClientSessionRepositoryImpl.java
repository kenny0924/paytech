package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerRegistrationMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerAuthenticationMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientSessionRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** ClientSession 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class ClientSessionRepositoryImpl implements ClientSessionRepository {
    private final CustomerRegistrationMapper registration;
    private final CustomerAuthenticationMapper authentication;

    /** 读取指定 CLIENT 会话。 */
    @Override
    public ClientSession session(long sessionId) {
        return authentication.session(sessionId);
    }

    /** 锁定 CLIENT 会话并刷新查询缓存。 */
    @Override
    public ClientSession lockSession(long sessionId) {
        return authentication.lockSession(sessionId);
    }

    /** 保存新的 CLIENT 账号或租户上下文会话。 */
    @Override
    public void insertSession(Issue issue) {
        changed(authentication.insertSession(issue));
    }

    /** 按原版本推进会话当前刷新代与空闲期限。 */
    @Override
    public void advanceSession(ClientSession row, Issue issue) {
        changed(authentication.advanceSession(row.version(), issue));
        row.generationNo(issue.generation());
        row.lastActivityTime(issue.issuedTime());
        row.idleExpireTime(issue.idleExpireTime());
        row.version(row.version() + 1);
    }

    /** 终止活动 CLIENT 会话，已结束会话保持原终态。 */
    @Override
    public void revokeSession(ClientSession row, OperationsSessionStatus status, String reason, Instant now) {
        if (row.status() != OperationsSessionStatus.ACTIVE) { return;
        } changed(authentication.revokeSession(row.id(), status, reason, now));
        row.status(status);
        row.version(row.version() + 1);
    }

    /** 密码重置时撤销该 CUSTOMER 账号全部 Channel 的会话。 */
    @Override
    public void revokeAccountSessions(long accountId, Instant now) {
        registration.revokeAccountSessions(accountId, now);
    }

    /** 条件更新必须准确修改一行；失败时不更新内存对象。 */
    void changed(int count) {
        if (count != 1) { throw new ConcurrentRequestException("认证状态已变化，请重新操作"); }
    }
}
