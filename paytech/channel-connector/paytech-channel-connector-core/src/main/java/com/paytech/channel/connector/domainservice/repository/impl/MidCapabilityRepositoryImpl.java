package com.paytech.payment.channel.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.payment.channel.core.dal.dataobject.MidCapability;
import com.paytech.payment.channel.core.dal.mapper.MidCapabilityMapper;
import com.paytech.payment.channel.domainservice.repository.MidCapabilityRepository;

import lombok.AllArgsConstructor;

/** MidCapability 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class MidCapabilityRepositoryImpl implements MidCapabilityRepository {
    /** 本表 SQL 映射。 */
    private final MidCapabilityMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<MidCapability> findByMidIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findByMidIds(ids);
    }

}
