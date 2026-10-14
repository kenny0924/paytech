package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientBusinessClassification;
import com.paytech.client.contract.core.dal.mapper.ClientBusinessClassificationMapper;
import com.paytech.client.contract.domainservice.repository.ClientBusinessClassificationRepository;

import lombok.AllArgsConstructor;

/** 只通过 ClientBusinessClassificationMapper 读取 t_client_business_classification。 */
@AllArgsConstructor
public class ClientBusinessClassificationRepositoryImpl implements ClientBusinessClassificationRepository {
    private final ClientBusinessClassificationMapper mapper;

    /** 读取本 Client Checkout 范围内的 client_business_classification 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ClientBusinessClassification> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 某经营资料的全部分类。 */
    @Override
    public List<com.paytech.client.contract.core.dal.dataobject.ClientClassification> findByProfile(long businessProfileId) {
        return mapper.findByProfile(businessProfileId);
    }

    /** 删除某经营资料的全部分类。 */
    @Override
    public void deleteByProfile(long businessProfileId) {
        mapper.deleteByProfile(businessProfileId);
    }

    /** 新增一条分类。 */
    @Override
    public void insert(com.paytech.client.contract.core.dal.dataobject.ClientClassification row) {
        if (mapper.insert(row) != 1) {
            throw new com.paytech.foundation.common.error.ErrorException("分类写入行数异常");
        }
    }
}
