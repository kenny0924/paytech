package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermPrice;
import com.paytech.commercial.core.dal.mapper.CommercialTermPriceMapper;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermPriceMapper 读取 t_commercial_term_price。 */
@AllArgsConstructor
public class CommercialTermPriceRepositoryImpl implements CommercialTermPriceRepository {
    private final CommercialTermPriceMapper mapper;

    /** 读取本 Client Checkout 范围内的 commercial_term_price 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CommercialTermPrice> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
