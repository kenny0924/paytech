package com.paytech.commercial.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.commercial.domain.RateCardQuery;
import com.paytech.commercial.domain.RateCardView;
import com.paytech.commercial.domainservice.service.RateCardQueryService;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** Client 费率卡读取（technique.md §2.7）：只读，不加锁、不开显式事务，组合查询由 RateCardQueryService 完成。 */
@Component
@AllArgsConstructor
public class RateCardQueryProcessor extends AbstractProcessor<RateCardQuery, RateCardView> {
    private final RateCardQueryService rateCardQueryService;

    @Override
    protected RateCardView processRequest(RateCardQuery query) {
        return rateCardQueryService.load(query);
    }
}
