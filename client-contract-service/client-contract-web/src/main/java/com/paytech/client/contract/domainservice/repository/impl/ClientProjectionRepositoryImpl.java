package com.paytech.client.contract.domainservice.repository.impl;

import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.contract.core.dal.mapper.ClientProjectionMapper;
import com.paytech.client.contract.domainservice.repository.ClientProjectionRepository;

/** 保持同一 CCT MyBatis 会话与数据源。 */
public final class ClientProjectionRepositoryImpl implements ClientProjectionRepository {
    private final ClientProjectionMapper mapper;
    public ClientProjectionRepositoryImpl(ClientProjectionMapper mapper) { this.mapper = mapper; }
    @Override public ClientProjectionHistory source(long changeId) { return mapper.source(changeId); }
    @Override public ClientProjectionHistory sourceAt(long clientId, int version) { return mapper.sourceAt(clientId, version); }
}
