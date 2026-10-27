package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.PartyIdentityKey;

/** t_party_identity_key 持久化；唯一索引是身份归属的最终保护。 */
public interface PartyIdentityKeyRepository {
    /** 按唯一身份（类型、作用域、Token 或公开登记号）读取，不存在返回 null。 */
    PartyIdentityKey find(String identifierType, String issuerScopeKey, String keyValue);

    /** 新增身份键；唯一索引冲突以 DuplicateKeyException 抛出。 */
    void insert(PartyIdentityKey key);
}
