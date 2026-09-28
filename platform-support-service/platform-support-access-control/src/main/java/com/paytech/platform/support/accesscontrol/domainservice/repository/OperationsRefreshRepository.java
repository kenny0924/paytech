package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;

/** Operations 刷新代事实访问。 */
public interface OperationsRefreshRepository {
    /** 按公开查找段读取刷新代；仍需验证秘密和会话归属。 */
    OperationsRefresh refresh(String lookupId);

    /** 保存唯一后继刷新代。 */
    void insert(OperationsRefresh refresh, Instant now);

    /** 单次消费当前刷新代。 */
    void consume(OperationsRefresh refresh, Instant now);

    /** 为已消费刷新代绑定后继。 */
    void link(OperationsRefresh refresh, long nextId);

    /** 撤销会话中所有未消费刷新代，并同步已持有的活动对象。 */
    void revoke(OperationsRefresh refresh, Instant now);

    /** 改密或账号关闭时撤销全部未消费刷新代。 */
    void revokeAccount(long accountId, Instant now);

}
