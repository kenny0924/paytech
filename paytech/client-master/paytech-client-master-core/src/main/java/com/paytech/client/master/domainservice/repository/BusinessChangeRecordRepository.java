package com.paytech.client.master.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;

/** t_business_change_record 的本地仓储；不负责其他表的加载或事务。 */
public interface BusinessChangeRecordRepository {
    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(long clientId);

    /** 读取一个业务根；不存在时返回 null。 */
    BusinessChangeRecord findRoot(BusinessChangeRecordRecordType rootType, String rootId);

    /** 补建 current_version=1 的业务根；并发补建由 uk_bcr_record 拒绝。 */
    void insertRoot(long historyId, BusinessChangeRecordRecordType rootType, String rootId, Instant now);

    /** 根 CAS 推进一个版本；版本已变化时抛出 ENTITY_CHANGE_VERSION_CONFLICT。 */
    void advanceRoot(long historyId, long expectedVersion, Instant now);
}
