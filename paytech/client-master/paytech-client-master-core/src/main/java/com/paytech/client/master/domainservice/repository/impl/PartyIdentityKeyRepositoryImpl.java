package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.PartyIdentityKey;
import com.paytech.client.master.core.dal.mapper.PartyIdentityKeyMapper;
import com.paytech.client.master.domainservice.repository.PartyIdentityKeyRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 身份键单表持久化。 */
@AllArgsConstructor
public class PartyIdentityKeyRepositoryImpl implements PartyIdentityKeyRepository {
    private final PartyIdentityKeyMapper mapper;

    /** 按唯一身份读取。 */
    @Override
    public PartyIdentityKey find(String identifierType, String issuerScopeKey, String normalizedValue) {
        return mapper.find(identifierType, issuerScopeKey, normalizedValue);
    }

    /** 新增身份键。 */
    @Override
    public void insert(PartyIdentityKey key) {
        if (mapper.insert(key) != 1) {
            throw new ErrorException("身份键写入行数异常");
        }
    }
}
