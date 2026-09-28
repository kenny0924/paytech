package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.PartyProfile;

/** t_party_profile 的持久化查询。 */
public interface PartyProfileMapper {
    /** 读取本 Client Checkout 范围内的 party_profile 记录，保留历史和未生效事实供业务判断。 */
    List<PartyProfile> findForCheckout(@Param("clientId") long clientId);
}
