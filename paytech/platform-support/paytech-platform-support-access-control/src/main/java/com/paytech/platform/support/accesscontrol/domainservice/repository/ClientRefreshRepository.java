package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Issue;

/** ClientRefresh 单表持久化合同；更新成功后同步传入对象。 */
public interface ClientRefreshRepository {
    /** 按公开查找段读取 CLIENT 刷新代。 */
    ClientRefresh refresh(String lookup);

    /** 在会话锁之后锁定刷新代。 */
    ClientRefresh lockRefresh(String lookup);

    /** 保存唯一活动刷新代，仅持久化 HMAC。 */
    void insertRefresh(Issue issue);

    /** 原子消费当前有效刷新代。 */
    void consumeRefresh(ClientRefresh row, Instant now);

    /** 登记已消费刷新代的唯一后继。 */
    void linkRefresh(ClientRefresh row, long nextId);

    /** 撤销指定 CLIENT 会话的全部活动刷新代。 */
    void revokeRefresh(long sessionId, Instant now);

    /** 撤销该 CUSTOMER 账号全部会话下的活动刷新代。 */
    void revokeAccountRefresh(long accountId, Instant now);

}
