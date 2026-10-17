package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ConsumerLegalDocument;

/** t_consumer_legal_document 的持久化查询。 */
public interface ConsumerLegalDocumentMapper {
    /** 读取本 Client Checkout 范围内的 consumer_legal_document 记录，保留历史和未生效事实供业务判断。 */
    List<ConsumerLegalDocument> findForCheckout(@Param("clientId") long clientId);
}
