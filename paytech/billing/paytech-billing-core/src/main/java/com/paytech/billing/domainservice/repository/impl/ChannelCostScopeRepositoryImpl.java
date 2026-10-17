package com.paytech.billing.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.ChannelCostScope;
import com.paytech.billing.core.dal.mapper.ChannelCostScopeMapper;
import com.paytech.billing.domainservice.repository.ChannelCostScopeRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** ChannelCostScopeRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChannelCostScopeRepositoryImpl implements ChannelCostScopeRepository {
    private final ChannelCostScopeMapper mapper;

    /** 批量写入一个版本的适用范围，写入行数必须与输入一致。 */
    @Override
    public void insertAll(List<ChannelCostScope> scopes) {
        if (mapper.insertBatch(scopes) != scopes.size()) {
            throw new ErrorException("渠道成本适用范围写入数量不一致: " + scopes.size());
        }
    }

    /** 版本全部适用范围，按范围序号升序。 */
    @Override
    public List<ChannelCostScope> findByVersion(long costVerId) {
        return mapper.selectByVersion(costVerId);
    }

    /** 版本内六个维度逐值相等的适用范围，不存在返回空。 */
    @Override
    public ChannelCostScope findMatch(long costVerId, long midId, String entityNo, String countryCode, String currency,
        String paymentMethodCode, String actionType) {
        return mapper.selectMatch(costVerId, midId, entityNo, countryCode, currency, paymentMethodCode, actionType);
    }
}
