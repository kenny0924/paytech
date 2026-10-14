package com.paytech.client.contract.domainservice.repository;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Client;

/** t_client 的本地仓储；不负责其他表的加载或事务。 */
public interface ClientRepository {
    /** 按 Client 身份读取客户；不存在时返回 null。 */
    Client findById(long clientId);

    /** 入驻通过后 PENDING 条件转为 ACTIVE；状态已变化时抛出并发冲突。 */
    void activate(long clientId, java.time.Instant now);
}
