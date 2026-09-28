package com.paytech.payment.execution.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;
import com.paytech.payment.execution.core.dal.mapper.ChannelTransactionRefMapper;
import com.paytech.payment.execution.domainservice.repository.ChannelTransactionRefRepository;

import lombok.AllArgsConstructor;

/** 渠道交易引用持久化 */
@Repository
@AllArgsConstructor
public class ChannelTransactionRefRepositoryImpl implements ChannelTransactionRefRepository {
    private final ChannelTransactionRefMapper channelTransactionRefMapper;

    /** 确认资金引用实际插入，重复外部交易由唯一约束阻止。 */
    @Override
    public void insertRef(ChannelTransactionRef ref) {
        if (channelTransactionRefMapper.insertRef(ref) != 1) {
            throw new ConcurrentRequestException("渠道交易引用未能保存");
        }
    }
}
