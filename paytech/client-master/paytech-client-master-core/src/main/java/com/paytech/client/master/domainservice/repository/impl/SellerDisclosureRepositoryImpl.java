package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.SellerDisclosure;
import com.paytech.client.master.core.dal.mapper.SellerDisclosureMapper;
import com.paytech.client.master.domainservice.repository.SellerDisclosureRepository;

import lombok.AllArgsConstructor;

/** 只通过 SellerDisclosureMapper 读取 t_seller_disclosure。 */
@AllArgsConstructor
public class SellerDisclosureRepositoryImpl implements SellerDisclosureRepository {
    private final SellerDisclosureMapper mapper;

    /** 读取本 Client Checkout 范围内的 seller_disclosure 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<SellerDisclosure> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 读取一个 OE 的全部卖方披露。 */
    @Override
    public List<SellerDisclosure> findByEntityNo(String entityNo) {
        return List.copyOf(mapper.findByEntityNo(entityNo));
    }
}
