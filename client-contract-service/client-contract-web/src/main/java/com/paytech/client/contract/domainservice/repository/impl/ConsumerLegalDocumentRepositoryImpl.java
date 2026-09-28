package com.paytech.client.contract.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ConsumerLegalDocument;
import com.paytech.client.contract.core.dal.mapper.ConsumerLegalDocumentMapper;
import com.paytech.client.contract.domainservice.repository.ConsumerLegalDocumentRepository;

import lombok.AllArgsConstructor;

/** 只通过 ConsumerLegalDocumentMapper 读取 t_consumer_legal_document。 */
@AllArgsConstructor
public class ConsumerLegalDocumentRepositoryImpl implements ConsumerLegalDocumentRepository {
    private final ConsumerLegalDocumentMapper mapper;

    /** 读取本 Client Checkout 范围内的 consumer_legal_document 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<ConsumerLegalDocument> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }
}
