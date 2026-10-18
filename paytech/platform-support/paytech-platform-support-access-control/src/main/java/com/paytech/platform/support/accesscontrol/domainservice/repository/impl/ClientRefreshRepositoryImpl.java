package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerRegistrationMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerAuthenticationMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientRefreshRepository;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** ClientRefresh 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class ClientRefreshRepositoryImpl implements ClientRefreshRepository {
    private final CustomerRegistrationMapper registration;
    private final CustomerAuthenticationMapper authentication;

    /** 按公开查找段读取 CLIENT 刷新代。 */
    @Override
    public ClientRefresh refresh(String lookup) {
        return authentication.refresh(lookup);
    }

    /** 在会话锁之后锁定刷新代。 */
    @Override
    public ClientRefresh lockRefresh(String lookup) {
        return authentication.lockRefresh(lookup);
    }

    /** 保存唯一活动刷新代，仅持久化 HMAC。 */
    @Override
    public void insertRefresh(Issue issue) {
        changed(authentication.insertRefresh(issue));
    }

    /** 原子消费当前有效刷新代。 */
    @Override
    public void consumeRefresh(ClientRefresh row, Instant now) {
        changed(authentication.consumeRefresh(row.id(), row.sessionId(), row.generationNo(), now));
        row.status(OperationsRefreshStatus.CONSUMED);
    }

    /** 登记已消费刷新代的唯一后继。 */
    @Override
    public void linkRefresh(ClientRefresh row, long nextId) {
        changed(authentication.linkRefresh(row.id(), nextId));
    }

    /** 撤销指定 CLIENT 会话的全部活动刷新代。 */
    @Override
    public void revokeRefresh(long sessionId, Instant now) {
        authentication.revokeRefresh(sessionId, now);
    }

    /** 撤销该 CUSTOMER 账号全部会话下的活动刷新代。 */
    @Override
    public void revokeAccountRefresh(long accountId, Instant now) {
        registration.revokeAccountRefresh(accountId, now);
    }

    /** 条件更新必须准确修改一行；失败时不更新内存对象。 */
    void changed(int count) {
        if (count != 1) { throw new ConcurrentRequestException("认证状态已变化，请重新操作"); }
    }
}
