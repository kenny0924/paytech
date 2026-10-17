package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.PaymentMethodProfile;
import com.paytech.commercial.core.dal.mapper.PaymentMethodProfileMapper;
import com.paytech.commercial.domainservice.repository.PaymentMethodProfileRepository;

import lombok.AllArgsConstructor;

/** 只通过 PaymentMethodProfileMapper 读取 t_payment_method_profile。 */
@AllArgsConstructor
public class PaymentMethodProfileRepositoryImpl implements PaymentMethodProfileRepository {
    private final PaymentMethodProfileMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 payment_method_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<PaymentMethodProfile> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
