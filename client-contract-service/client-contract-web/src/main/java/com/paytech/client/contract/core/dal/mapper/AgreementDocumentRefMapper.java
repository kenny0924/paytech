package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.AgreementDocumentRef;

/** t_agreement_document_ref 的持久化查询。 */
public interface AgreementDocumentRefMapper {
    /** 读取本 Client Checkout 范围内的 agreement_document_ref 记录，保留历史和未生效事实供业务判断。 */
    List<AgreementDocumentRef> findForCheckout(@Param("clientId") long clientId);
}
