package com.paytech.channel.connector.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.dal.mapper.MidCapabilityDetailMapper;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;
import com.paytech.foundation.common.error.ErrorException;

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

    /** 批量写入明细；行数与条数不符属于系统错误。 */
    @Override
    public void insertAll(List<MidCapabilityDetail> details) {
        if (details.isEmpty()) {
            return;
        }
        if (mapper.insertAll(details) != details.size()) {
            throw new ErrorException("能力明细写入行数不符: " + details.getFirst().getMidCapabilityId());
        }
    }

    /** 删除能力组的全部明细。 */
    @Override
    public void deleteByCapabilityId(long midCapabilityId) {
        mapper.deleteByCapabilityId(midCapabilityId);
    }
}
