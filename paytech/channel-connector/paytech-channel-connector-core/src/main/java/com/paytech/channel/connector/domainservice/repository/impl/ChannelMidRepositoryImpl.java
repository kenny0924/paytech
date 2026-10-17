package com.paytech.payment.channel.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.payment.channel.core.dal.dataobject.ChannelMid;
import com.paytech.payment.channel.core.dal.mapper.ChannelMidMapper;
import com.paytech.payment.channel.domainservice.repository.ChannelMidRepository;

import lombok.AllArgsConstructor;

/** ChannelMid 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class ChannelMidRepositoryImpl implements ChannelMidRepository {
    /** 本表 SQL 映射。 */
    private final ChannelMidMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<ChannelMid> findByAccountIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByAccountIds(ids);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public ChannelMid findById(long id) {
        return mapper.findById(id);
    }
}
