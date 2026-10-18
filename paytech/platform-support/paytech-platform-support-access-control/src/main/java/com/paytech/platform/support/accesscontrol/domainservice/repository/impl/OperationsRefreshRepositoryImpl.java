package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRefreshRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import org.springframework.stereotype.Repository;
import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsRefreshRepositoryImpl implements OperationsRefreshRepository {
    private final OperationsIdentityMapper mapper;
    /** 按公开查找段读取刷新代；仍需验证秘密和会话归属。 */
    @Override
    public OperationsRefresh refresh(String lookupId) {
        return mapper.refresh(lookupId);
    }

    /** 保存唯一后继刷新代。 */
    @Override
    public void insert(OperationsRefresh refresh, Instant now) {
        requireOne(mapper.insertRefresh(refresh, now));
    }

    /** 单次消费当前刷新代。 */
    @Override
    public void consume(OperationsRefresh refresh, Instant now) {
        requireOne(mapper.consumeRefresh(refresh.id(), refresh.generationNo(), now));
        refresh.status(OperationsRefreshStatus.CONSUMED);
    }

    /** 为已消费刷新代绑定后继。 */
    @Override
    public void link(OperationsRefresh refresh, long nextId) {
        requireOne(mapper.linkRefresh(refresh.id(), nextId));
    }

    /** 撤销会话中所有未消费刷新代，并同步已持有的活动对象。 */
    @Override
    public void revoke(OperationsRefresh refresh, Instant now) {
        mapper.revokeRefresh(refresh.sessionId(), now);
        if (refresh.status() == OperationsRefreshStatus.ISSUED) { refresh.status(OperationsRefreshStatus.REVOKED); }
    }

    /** 改密或账号关闭时撤销全部未消费刷新代。 */
    @Override
    public void revokeAccount(long accountId, Instant now) {
        mapper.revokeAccountRefresh(accountId, now);
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }
}
