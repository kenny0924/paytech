package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** t_business_change_record（commercial 实例）的本地仓储；不负责其他表的加载或事务。 */
public interface BusinessChangeRecordRepository {
    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(CommercialCheckoutScope scope);
}
