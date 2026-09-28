package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Agreement;

/** t_agreement 的持久化查询。 */
public interface AgreementMapper {
    /** 读取本 Client Checkout 范围内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    List<Agreement> findForCheckout(@Param("clientId") long clientId);
}
