package com.paytech.client.master.domainservice.repository;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.PartyProfileIdentifier;

/** t_party_profile_identifier 的身份绑定持久化。 */
public interface PartyProfileIdentifierRepository {
    /** 读取资料唯一的证件行，不存在返回 null。 */
    PartyProfileIdentifier findByProfile(long profileId);

    /** 以原绑定值为条件绑定身份键，成功后同步原对象；行已变化时抛出并发冲突。 */
    void bind(PartyProfileIdentifier identifier, long identityKeyId, String issuerScopeKey, Instant now);
}
