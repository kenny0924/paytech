package com.paytech.client.contract.domainservice.repository;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;

/** t_business_change_record 的本地仓储；不负责其他表的加载或事务。 */
public interface BusinessChangeRecordRepository {
    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(long clientId);
}
