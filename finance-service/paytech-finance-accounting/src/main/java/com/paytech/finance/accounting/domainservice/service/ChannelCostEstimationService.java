package com.paytech.finance.accounting.domainservice.service;

import com.paytech.finance.accounting.domain.ChannelCostEstimateCommand;
import com.paytech.finance.accounting.domain.view.ChannelCostEstimate;

/**
 * 按精确唯一命中的渠道成本规则估算预计渠道成本
 * <li>只读本模块数据库，不开启事务，可在调用方事务内执行；不调用外部依赖。</li>
 * <li>无方案、无覆盖发生时间的生效版本、无命中范围时拒绝估算，不默认零成本。</li>
 */
public interface ChannelCostEstimationService {

    /**
     * 估算预计渠道成本
     *
     * @param command 渠道路由元组、计费基数与发生时间
     * @return 命中版本、范围与逐组成金额及证据
     */
    ChannelCostEstimate estimate(ChannelCostEstimateCommand command);
}
