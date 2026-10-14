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
    /** 最新历史。 */
    @Override public ClientProjectionHistory latest(long clientId) { return mapper.latest(clientId); }
    /** 先推进根再写版本，根版本条件保证同一 Client 生命周期串行。 */
    @Override public void append(ClientProjectionHistory next, String snapshot) {
        if (mapper.advanceRoot(next) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("Client 生命周期历史已变化");
        }
        if (mapper.insertVersion(next, snapshot) != 1) {
            throw new com.paytech.foundation.common.error.ErrorException("Client 生命周期历史写入行数异常");
        }
    }
}
