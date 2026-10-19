package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;

/** t_business_change_record（commercial 实例）的本地仓储；不负责其他表的加载或事务。 */
public interface BusinessChangeRecordRepository {
    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(CommercialCheckoutScope scope);

    /**
     * 读取一个聚合根的 Root 当前修订版本（expected_change_version CAS 令牌）。
     *
     * @param rootType 聚合根类型
     * @param rootId 聚合根 ID
     * @return 当前修订版本，Root 不存在为 null
     */
    Long findRootVersion(BusinessChangeRecordAggregateRootType rootType, String rootId);
}
