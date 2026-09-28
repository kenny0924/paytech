package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.ClientBusinessProfile;

/** t_client_business_profile 的持久化查询。 */
public interface ClientBusinessProfileMapper {
    /** 读取本 Client Checkout 范围内的 client_business_profile 记录，保留历史和未生效事实供业务判断。 */
    List<ClientBusinessProfile> findByClientId(@Param("clientId") long clientId);
}
