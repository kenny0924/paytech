package com.paytech.payment.execution.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;
import com.paytech.payment.execution.core.dal.mapper.ChannelTransactionRefMapper;
import com.paytech.payment.execution.domainservice.repository.ChannelTransactionRefRepository;

import lombok.AllArgsConstructor;

/** 渠道交易引用持久化 */
@Repository
@AllArgsConstructor
public class ChannelTransactionRefRepositoryImpl implements ChannelTransactionRefRepository {
    private final IdGenerator idGenerator;
    private final ChannelTransactionRefMapper channelTransactionRefMapper;

    /** 分配内部 ID 并确认资金引用实际插入，重复外部交易由唯一约束阻止。 */
    @Override
    public void insertRef(ChannelTransactionRef ref) {
        ref.setChannelTransactionRefId(idGenerator.nextId());
        if (channelTransactionRefMapper.insertRef(ref) != 1) {
            throw new ConcurrentRequestException("渠道交易引用未能保存");
        }
    }
}
