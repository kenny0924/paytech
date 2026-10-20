package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.mapper.MidCapabilityMapper;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

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

    /** 读取指定能力组。 */
    @Override
    public MidCapability findById(long id) {
        return mapper.findById(id);
    }

    /** 写入新能力组；唯一索引保护 MID 内的六维组合。 */
    @Override
    public void insert(MidCapability capability) {
        try {
            mapper.insert(capability);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("MID 已有相同条件的能力组: MID " + capability.getMidId() + ", "
                + capability.getPaymentMethodCode() + "/" + capability.getSchemeCode() + "/"
                + capability.getCountryCode() + "/" + capability.getCurrency() + "/" + capability.getMccCode(), e);
        }
    }

    /** 以原状态为条件启停，成功后同步原对象。 */
    @Override
    public void updateStatus(MidCapability capability, CapabilityStatus toStatus, Instant now) {
        if (mapper.updateStatus(capability.getMidCapabilityId(), capability.getCapabilityStatus(), toStatus, now) != 1) {
            throw new ConcurrentRequestException("能力组状态已变化: " + capability.getMidCapabilityId());
        }
        capability.setCapabilityStatus(toStatus);
        capability.setUpdateTime(now);
    }

    /** 刷新修改时间，成功后同步原对象。 */
    @Override
    public void touch(MidCapability capability, Instant now) {
        if (mapper.touch(capability.getMidCapabilityId(), now) != 1) {
            throw new ErrorException("能力组在写入时不存在: " + capability.getMidCapabilityId());
        }
        capability.setUpdateTime(now);
    }
}
