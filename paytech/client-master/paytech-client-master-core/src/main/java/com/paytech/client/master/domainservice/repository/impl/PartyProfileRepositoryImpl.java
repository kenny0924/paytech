package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.PartyProfile;
import com.paytech.client.master.core.dal.mapper.PartyProfileMapper;
import com.paytech.client.master.domainservice.repository.PartyProfileRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 PartyProfileMapper 读取 t_party_profile。 */
@AllArgsConstructor
public class PartyProfileRepositoryImpl implements PartyProfileRepository {
    private final PartyProfileMapper mapper;

    /** 读取 Client 本身法律主体的资料，供候选查询判断主体是否已批准。 */
    @Override
    public List<PartyProfile> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 按资料 ID 读取资料；空集合不访问数据库。 */
    @Override
    public List<PartyProfile> findByIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.findByIds(ids));
    }

    /** 首次批准时写入法律主体资料的批准时间；必须恰好更新一行。 */
    @Override
    public void markApproved(long partyId, Instant now) {
        if (mapper.markApproved(partyId, now) != 1) {
            throw new ErrorException("法律主体资料不存在或已记录批准时间: " + partyId);
        }
    }
}
