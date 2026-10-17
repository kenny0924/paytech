package com.paytech.channel.connector.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.dal.mapper.MidCapabilityDetailMapper;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;

import lombok.AllArgsConstructor;

/** MidCapabilityDetail 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class MidCapabilityDetailRepositoryImpl implements MidCapabilityDetailRepository {
    /** 本表 SQL 映射。 */
    private final MidCapabilityDetailMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<MidCapabilityDetail> findByCapabilityIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByCapabilityIds(ids);
    }

}
