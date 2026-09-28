package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.PaymentMethodProfile;
import com.paytech.client.contract.core.dal.mapper.PaymentMethodProfileMapper;
import com.paytech.client.contract.domainservice.repository.PaymentMethodProfileRepository;

import lombok.AllArgsConstructor;

/** 只通过 PaymentMethodProfileMapper 读取 t_payment_method_profile。 */
@AllArgsConstructor
public class PaymentMethodProfileRepositoryImpl implements PaymentMethodProfileRepository {
    private final PaymentMethodProfileMapper mapper;

    /** 读取本 Client Checkout 范围内的 payment_method_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<PaymentMethodProfile> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
