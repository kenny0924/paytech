package com.paytech.client.contract.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.PartyProfile;
import com.paytech.client.contract.core.dal.mapper.PartyProfileMapper;
import com.paytech.client.contract.domainservice.repository.PartyProfileRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 PartyProfileMapper 读取 t_party_profile。 */
@AllArgsConstructor
public class PartyProfileRepositoryImpl implements PartyProfileRepository {
    private final PartyProfileMapper mapper;

    /** 读取本 Client Checkout 范围内的 party_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<PartyProfile> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 首次批准时写入法律主体资料的批准时间；必须恰好更新一行。 */
    @Override
    public void markApproved(long partyId, Instant now) {
        if (mapper.markApproved(partyId, now) != 1) {
            throw new ErrorException("法律主体资料不存在或已记录批准时间: " + partyId);
        }
    }
}
