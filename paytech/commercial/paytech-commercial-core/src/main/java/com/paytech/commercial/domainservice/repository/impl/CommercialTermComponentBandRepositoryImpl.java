package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.mapper.CommercialTermComponentBandMapper;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermComponentBandMapper 读取 t_commercial_term_component_band。 */
@AllArgsConstructor
public class CommercialTermComponentBandRepositoryImpl implements CommercialTermComponentBandRepository {
    private final CommercialTermComponentBandMapper mapper;

    /** 读取本 Client Checkout 范围内的 commercial_term_component_band 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CommercialTermComponentBand> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
