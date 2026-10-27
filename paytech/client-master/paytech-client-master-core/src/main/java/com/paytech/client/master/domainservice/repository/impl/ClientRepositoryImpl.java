package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.Client;
import com.paytech.client.master.core.dal.mapper.ClientMapper;
import com.paytech.client.master.domain.entityadmin.ClientSummaryView;
import com.paytech.client.master.domainservice.repository.ClientRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientMapper 读取 t_client。 */
@AllArgsConstructor
public class ClientRepositoryImpl implements ClientRepository {
    private final ClientMapper mapper;

    /** 按 Client 身份读取客户；不存在时返回 null。 */
    @Override
    public Client findById(long clientId) {
        return mapper.findById(clientId);
    }

    /** 条件激活。 */
    @Override
    public void activate(long clientId, java.time.Instant now) {
        if (mapper.activate(clientId, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("Client 状态已变化");
        }
    }

    /** 运营 Client 分页。 */
    @Override
    public java.util.List<com.paytech.client.master.domain.onboardingadmin.ClientAdminView> pageForAdmin(String status,
        String jurisdictionCode, Long responsiblePartnerId, int limit, int offset) {
        return java.util.List.copyOf(mapper.pageForAdmin(status, jurisdictionCode, responsiblePartnerId, limit, offset));
    }

    /** 总数。 */
    @Override
    public long countForAdmin(String status, String jurisdictionCode, Long responsiblePartnerId) {
        return mapper.countForAdmin(status, jurisdictionCode, responsiblePartnerId);
    }

    /** 详情行。 */
    @Override
    public com.paytech.client.master.domain.onboardingadmin.ClientAdminView findAdminView(long clientId) {
        return mapper.findAdminView(clientId);
    }

    /** ACTIVE 条件转为 SUSPENDED；必须恰好更新一行。 */
    @Override
    public void suspend(long clientId, java.time.Instant now) {
        if (mapper.suspend(clientId, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("Client 状态已变化");
        }
    }

    /** 读取 Client 主体摘要；不存在时返回 null。 */
    @Override
    public ClientSummaryView findSummary(long clientId) {
        return mapper.findSummary(clientId);
    }
}
