package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.mapper.AgreementOperatingEntityMapper;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;

import lombok.AllArgsConstructor;

/** 只通过 AgreementOperatingEntityMapper 读取 t_agreement_operating_entity。 */
@AllArgsConstructor
public class AgreementOperatingEntityRepositoryImpl implements AgreementOperatingEntityRepository {
    private final AgreementOperatingEntityMapper mapper;

    /** 读取本 Client Checkout 范围内的 agreement_operating_entity 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<AgreementOperatingEntity> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
