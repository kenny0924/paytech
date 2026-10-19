package com.paytech.billing.domainservice.service;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.billing.domain.ChannelCostComponentAmount;
import com.paytech.billing.domain.ChannelCostComponentRule;

/**
 * 渠道成本"组件 → 金额"的本地纯计算（技术方案第 2.1 节）
 * <li>输入为 channel-connector 命中接口返回的组件与计费基数、币种精度；不读库、不调用外部依赖、不开启事务。</li>
 * <li>本期没有调用方，保留给自动 PAYMENT 事实生成（需求 B）。</li>
 */
public interface ChannelCostCalculator {

    /**
     * 按输入顺序逐组件计算
     * <li>原始金额 = 基数 × 费率 + 固定费，按 8 位小数 HALF_UP，再受上下限约束；记账金额按币种精度与组件舍入方式归整。</li>
     *
     * @param components 命中的成本组件，至少一个
     * @param basisAmount 非负计费基数，小数位不超过币种精度
     * @param minorUnit 币种精度 0–8
     * @return 与输入顺序一致的逐组件金额
     * @throws com.paytech.foundation.common.error.BizCheckFailException 组件为空、基数或精度不合法
     */
    List<ChannelCostComponentAmount> calculate(List<ChannelCostComponentRule> components, BigDecimal basisAmount,
        int minorUnit);
}
