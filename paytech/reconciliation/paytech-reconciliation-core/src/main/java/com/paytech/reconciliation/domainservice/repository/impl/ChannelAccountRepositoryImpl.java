package com.paytech.reconciliation.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.reconciliation.core.dal.dataobject.ChannelAccount;
import com.paytech.reconciliation.core.dal.mapper.ChannelAccountMapper;
import com.paytech.reconciliation.domainservice.repository.ChannelAccountRepository;

import lombok.AllArgsConstructor;

/** ChannelAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChannelAccountRepositoryImpl implements ChannelAccountRepository {
    private final ChannelAccountMapper mapper;

    /** 按 MID 与币种读取唯一 ACTIVE 清算账户，不存在或未激活返回空。 */
    @Override
    public ChannelAccount findActiveByMid(long midId, String currency) {
        return mapper.selectActiveByMid(midId, currency);
    }
}
