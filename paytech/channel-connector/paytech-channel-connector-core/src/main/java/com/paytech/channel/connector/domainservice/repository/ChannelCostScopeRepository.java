package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;

/** t_channel_cost_scope：适用范围写入与查询；写入后不可变。 */
public interface ChannelCostScopeRepository {

    /** 批量写入一个版本的全部适用范围。 */
    void insertAll(List<ChannelCostScope> scopes);

    /** 版本全部适用范围，按范围序号升序。 */
    List<ChannelCostScope> findByVersion(long costVerId);

    /** 版本内六个维度逐值相等的适用范围，不存在返回空。 */
    ChannelCostScope findMatch(long costVerId, long midId, String entityNo, String countryCode, String currency,
        String paymentMethodCode, String actionType);
}
