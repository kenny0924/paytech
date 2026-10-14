package com.paytech.client.contract.domainservice.repository.impl;

import java.time.Instant;

import com.paytech.client.contract.core.dal.dataobject.PartyProfileIdentifier;
import com.paytech.client.contract.core.dal.mapper.PartyProfileIdentifierMapper;
import com.paytech.client.contract.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 资料证件行的身份绑定；以原绑定值为条件更新。 */
@AllArgsConstructor
public class PartyProfileIdentifierRepositoryImpl implements PartyProfileIdentifierRepository {
    private final PartyProfileIdentifierMapper mapper;

    /** 读取资料证件行。 */
    @Override
    public PartyProfileIdentifier findByProfile(long profileId) {
        return mapper.findByProfile(profileId);
    }

    /** 条件绑定身份键。 */
    @Override
    public void bind(PartyProfileIdentifier identifier, long identityKeyId, String issuerScopeKey, Instant now) {
        if (mapper.bind(identifier, identityKeyId, issuerScopeKey, now) != 1) {
            throw new ConcurrentRequestException("证件资料已变化，请刷新后重试");
        }
        identifier.setIdentityKeyId(identityKeyId);
        identifier.setIssuerScopeKey(issuerScopeKey);
    }
}
