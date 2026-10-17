package com.paytech.client.contract.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.PartyProfile;

/** t_party_profile 的本地仓储；不负责其他表的加载或事务。 */
public interface PartyProfileRepository {
    /** 读取本 Client Checkout 范围内的 party_profile 记录，保留历史和未生效事实供业务判断。 */
    List<PartyProfile> findForCheckout(long clientId);

    /**
     * 首次批准时写入法律主体资料的批准时间
     * <li>只更新 approved_time 为空的一行；影响行数不是 1 时抛出，由调用方事务整体回滚。</li>
     */
    void markApproved(long partyId, Instant now);
}
