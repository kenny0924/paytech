package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.CommercialTermVersion;

/** t_commercial_term_version 的持久化查询。 */
public interface CommercialTermVersionMapper {
    /** 读取本 Client Checkout 范围内的 commercial_term_version 记录，保留历史和未生效事实供业务判断。 */
    List<CommercialTermVersion> findForCheckout(@Param("clientId") long clientId);
}
