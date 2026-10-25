package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.PartnerRole;
import com.paytech.client.master.core.dal.mapper.PartnerRoleMapper;
import com.paytech.client.master.domain.enums.PartnerRoleStatus;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 只通过 PartnerRoleMapper 读写 t_partner_role。 */
@AllArgsConstructor
public class PartnerRoleRepositoryImpl implements PartnerRoleRepository {
    private final PartnerRoleMapper mapper;

    /** 读取本 Client Checkout 范围内 PARTNER 来源 Local Partner 的 ACTIVE 角色。 */
    @Override
    public List<PartnerRole> findActiveForCheckout(long clientId) {
        return List.copyOf(mapper.findActiveForCheckout(clientId));
    }

    /** 空集合不发 SQL。 */
    @Override
    public List<PartnerRole> findActiveByPartners(Collection<Long> localPartnerIds) {
        if (localPartnerIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.findActiveByPartners(localPartnerIds));
    }

    /** 插入一条 ACTIVE 角色行。 */
    @Override
    public void insert(PartnerRole role) {
        mapper.insert(role);
    }

    /** ACTIVE 条件结束，成功后同步状态、槽位与时间。 */
    @Override
    public void end(PartnerRole role, Instant now) {
        if (mapper.end(role.getPartnerRoleId(), now) != 1) {
            throw new ConcurrentRequestException("Local Partner 角色已变化");
        }
        role.setStatus(PartnerRoleStatus.ENDED);
        role.setActiveSlot(null);
        role.setUpdateTime(now);
    }
}
