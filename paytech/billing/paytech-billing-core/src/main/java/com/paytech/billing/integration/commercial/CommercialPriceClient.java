package com.paytech.billing.integration.commercial;

import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.OrderPaymentSucceededFact;

/**
 * 在订单冻结的条款版本内命中 commercial 价格；必须在数据库事务外调用
 * <li>命中参数按技术方案 4.6.2 / 4.6.3 由订单冻结商业字段与支付成功事实给出；卡属性为空的维度不传（事实未知）。</li>
 */
public interface CommercialPriceClient {

    /**
     * 命中 Client 定价（CLIENT_PRICING）
     *
     * @param payment 订单支付成功事实
     * @return 命中价格（含结算四字段）
     * @throws com.paytech.foundation.common.error.ErrorException 版本不存在、事实不完整、无命中、歧义、上游不可用或响应无效；事实停在重试
     */
    CommercialPrice resolveClientPricing(OrderPaymentSucceededFact payment);

    /**
     * 命中 Partner 合同下的条款（PARTNER_SHARE 或 PLATFORM_SERVICE_CHARGE）
     *
     * @param termType 条款类型代码
     * @param termVersionId 订单冻结的该条款版本
     * @param payment 订单支付成功事实，Partner 五项已确认齐全
     * @return 命中价格
     * @throws com.paytech.foundation.common.error.ErrorException 同 {@link #resolveClientPricing}
     */
    CommercialPrice resolvePartnerTerm(String termType, long termVersionId, OrderPaymentSucceededFact payment);
}
