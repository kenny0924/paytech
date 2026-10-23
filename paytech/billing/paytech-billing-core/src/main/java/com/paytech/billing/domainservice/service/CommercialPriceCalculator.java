package com.paytech.billing.domainservice.service;

import java.util.List;

import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.FeeCalculationBasis;
import com.paytech.billing.domain.PriceComponentAmount;

/**
 * commercial 价格"组件 → 金额"的本地纯计算（技术方案 4.6.1）
 * <li>只计算 triggerCode = SALE 的组件（S1），按 componentNo 升序；不读库、不调用外部依赖、不开启事务。</li>
 * <li>本期不能表达的定价（BUY_RATE_DIFFERENCE、PERIOD_VOLUME 阶梯、CLIENT_PRICING 下引用 Client 手续费的基数、FIXED 等绝对金额币种与事实
 * 不同、基数落不进任何档位、未知组件类型或阶梯模式）以业务拒绝失败，不按 0 计算（S9、S10）。</li>
 */
public interface CommercialPriceCalculator {

    /**
     * 计算一个命中价格的全部 SALE 组件
     *
     * @param price 命中价格
     * @param basis 可用基数、事实币种与精度
     * @return 按 componentNo 升序的组件金额，金额可能为零；没有 SALE 组件时为空列表
     * @throws com.paytech.foundation.common.error.BizCheckFailException 组件规则本期不能表达
     */
    List<PriceComponentAmount> calculate(CommercialPrice price, FeeCalculationBasis basis);
}
