package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientBusinessClassification;

/** t_client_business_classification 的持久化查询。 */
public interface ClientBusinessClassificationMapper {
    /** 读取本 Client Checkout 范围内的 client_business_classification 记录，保留历史和未生效事实供业务判断。 */
    List<ClientBusinessClassification> findForCheckout(@Param("clientId") long clientId);
}
