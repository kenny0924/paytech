package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostComponent;
import com.paytech.finance.accounting.core.dal.mapper.ChannelCostComponentMapper;
import com.paytech.finance.accounting.domainservice.repository.ChannelCostComponentRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** ChannelCostComponentRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChannelCostComponentRepositoryImpl implements ChannelCostComponentRepository {
    private final ChannelCostComponentMapper mapper;

    /** 批量写入一个版本的成本组成，写入行数必须与输入一致。 */
    @Override
    public void insertAll(List<ChannelCostComponent> components) {
        if (mapper.insertBatch(components) != components.size()) {
            throw new ErrorException("渠道成本组成写入数量不一致: " + components.size());
        }
    }

    /** 一个适用范围的全部组成，按组成代码升序。 */
    @Override
    public List<ChannelCostComponent> findByScope(long scopeId) {
        return mapper.selectByScope(scopeId);
    }

    /** 多个适用范围的全部组成，按范围主键、组成代码升序；范围为空时不查询。 */
    @Override
    public List<ChannelCostComponent> findByScopes(List<Long> scopeIds) {
        if (scopeIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByScopes(scopeIds);
    }
}
