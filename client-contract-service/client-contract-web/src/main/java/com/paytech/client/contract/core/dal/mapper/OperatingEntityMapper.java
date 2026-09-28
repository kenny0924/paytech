package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.OperatingEntity;

/** t_operating_entity 的持久化查询。 */
public interface OperatingEntityMapper {
    /** 读取本 Client Checkout 范围内的 operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<OperatingEntity> findForCheckout(@Param("clientId") long clientId);
}
