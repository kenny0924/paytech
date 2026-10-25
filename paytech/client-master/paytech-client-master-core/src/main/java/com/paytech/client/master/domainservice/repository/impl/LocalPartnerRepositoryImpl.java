package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.core.dal.mapper.LocalPartnerMapper;
import com.paytech.client.master.domain.entityadmin.LocalPartnerAdminView;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;

import lombok.AllArgsConstructor;

/** 只通过 LocalPartnerMapper 读取 t_local_partner。 */
@AllArgsConstructor
public class LocalPartnerRepositoryImpl implements LocalPartnerRepository {
    private final LocalPartnerMapper mapper;

    /** 读取本 Client Checkout 范围内的 local_partner 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<LocalPartner> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 按 ID 读取 Local Partner；不存在时返回 null。 */
    @Override
    public LocalPartner findById(long localPartnerId) {
        return mapper.findById(localPartnerId);
    }

    /** 读取一个 Local Partner 的运营组合行（不含角色）。 */
    @Override
    public LocalPartnerAdminView findAdminView(long localPartnerId) {
        return mapper.findAdminView(localPartnerId);
    }

    /** 分页读取运营组合行（不含角色）。 */
    @Override
    public List<LocalPartnerAdminView> pageForAdmin(String status, String role, int limit, int offset) {
        return List.copyOf(mapper.pageForAdmin(status, role, limit, offset));
    }

    /** 与分页相同筛选条件下的总数。 */
    @Override
    public long countForAdmin(String status, String role) {
        return mapper.countForAdmin(status, role);
    }
}
