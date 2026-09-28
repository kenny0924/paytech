package com.paytech.client.contract.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;

/** t_business_change_record 的持久化查询。 */
public interface BusinessChangeRecordMapper {
    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(@Param("clientId") long clientId);
}
