package com.paytech.channel.connector.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.mapper.ChannelAccountMapper;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;

import lombok.AllArgsConstructor;

/** ChannelAccount 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class ChannelAccountRepositoryImpl implements ChannelAccountRepository {
    /** 本表 SQL 映射。 */
    private final ChannelAccountMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<ChannelAccount> findByEntity(String entityNo) {
        return mapper.findByEntity(entityNo);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public ChannelAccount findById(long id) {
        return mapper.findById(id);
    }
}
