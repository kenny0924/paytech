package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.LocalPartner;

/** t_local_partner 的持久化查询。 */
public interface LocalPartnerMapper {
    /** 读取本 Client Checkout 范围内的 local_partner 记录，保留历史和未生效事实供业务判断。 */
    List<LocalPartner> findForCheckout(@Param("clientId") long clientId);
}
