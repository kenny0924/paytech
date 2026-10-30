package com.paytech.commercial.integration;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.domain.ChannelIdentityItem;
import com.paytech.commercial.domain.ChannelIdentityReadiness;

/** channel-connector 渠道身份就绪查询端口；只能在数据库事务外调用。 */
public interface ChannelIdentityReadinessClient {
    /**
     * 判定一个 OE 下若干组支付方式 × 币种 × MCC 是否就绪
     *
     * @param entityNo OE 编号
     * @param items 去重后的组合，1–200 组
     * @return 每组一条结论，按请求顺序
     *         <li>channel-connector 不可用、返回任何错误（含配置损坏 500）或响应不符合合同时抛出 OWNER_UNAVAILABLE（503），不返回部分结果。</li>
     */
    List<ChannelIdentityReadiness> checkReadiness(String entityNo, Collection<ChannelIdentityItem> items);
}
