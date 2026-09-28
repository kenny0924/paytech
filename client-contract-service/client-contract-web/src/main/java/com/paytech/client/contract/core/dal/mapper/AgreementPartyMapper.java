package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.AgreementParty;

/** t_agreement_party 的持久化查询。 */
public interface AgreementPartyMapper {
    /** 读取本 Client Checkout 范围内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    List<AgreementParty> findForCheckout(@Param("clientId") long clientId);
}
