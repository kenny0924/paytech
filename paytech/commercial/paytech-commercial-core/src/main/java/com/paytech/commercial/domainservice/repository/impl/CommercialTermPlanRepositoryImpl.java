package com.paytech.commercial.domainservice.repository.impl;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermPlan;
import com.paytech.commercial.core.dal.mapper.CommercialTermPlanMapper;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermPlanMapper 读取 t_commercial_term_plan。 */
@AllArgsConstructor
public class CommercialTermPlanRepositoryImpl implements CommercialTermPlanRepository {
    private final CommercialTermPlanMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_plan 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CommercialTermPlan> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }
}
