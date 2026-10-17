package com.paytech.billing.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.ChannelCostScope;

/** t_channel_cost_scope：渠道成本适用范围持久化，只插入与读取。 */
@Mapper
public interface ChannelCostScopeMapper {

    /** 批量插入适用范围。 */
    int insertBatch(@Param("scopes") List<ChannelCostScope> scopes);

    /** 版本全部适用范围，按范围序号升序。 */
    List<ChannelCostScope> selectByVersion(@Param("costVerId") long costVerId);

    /** 版本内六个维度逐值相等的适用范围。 */
    ChannelCostScope selectMatch(@Param("costVerId") long costVerId, @Param("midId") long midId,
        @Param("entityNo") String entityNo, @Param("countryCode") String countryCode,
        @Param("currency") String currency, @Param("paymentMethodCode") String paymentMethodCode,
        @Param("actionType") String actionType);
}
