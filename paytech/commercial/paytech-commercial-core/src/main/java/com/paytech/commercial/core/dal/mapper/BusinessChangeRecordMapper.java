package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;

/** t_business_change_record（commercial 实例）的持久化查询。 */
public interface BusinessChangeRecordMapper {
    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);
}
