package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.domain.CommercialPriceQuery;
import com.paytech.commercial.domain.CommercialPriceView;
import com.paytech.commercial.domain.PricingFacts;
import com.paytech.commercial.domainservice.service.CommercialPriceResolutionService;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 交易后价格命中（technique.md §2.4）：只读，不加锁、不开显式事务
 * <li>先解析受控事实（目录外维度或非法值 422 INVALID_PRICING_FACT），再由 CommercialPriceResolutionService 校验父链与时点适用关系并
 * 执行命中算法。</li>
 */
@Component
@AllArgsConstructor
public class ResolveCommercialPriceProcessor extends AbstractProcessor<CommercialPriceQuery, CommercialPriceView> {
    private final CommercialPriceResolutionService commercialPriceResolutionService;

    /** Client 条款与 Partner 条款的 Owner 恰好一个。 */
    @Override
    protected void validate(CommercialPriceQuery query) {
        if ((query.getClientId() == null) == (query.getLocalPartnerId() == null)) {
            throw new InvalidParameterException("clientId 与 localPartnerId 必须恰好一个");
        }
    }

    @Override
    protected CommercialPriceView processRequest(CommercialPriceQuery query) {
        PricingFacts facts = PricingFacts.parse(query.getPaymentMethodCode(), query.getCurrency(), query.getDimensions());
        return commercialPriceResolutionService.resolve(query, facts);
    }
}
