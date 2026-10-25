package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;

/** t_business_change_record 的持久化查询与根推进。 */
public interface BusinessChangeRecordMapper {
    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    List<BusinessChangeRecord> findCheckoutRoots(@Param("clientId") long clientId);

    /** 读取一个业务根；不存在时返回 null。 */
    BusinessChangeRecord findRoot(@Param("rootType") BusinessChangeRecordRecordType rootType, @Param("rootId") String rootId);

    /** 补建 current_version=1 的业务根。 */
    void insertRoot(@Param("historyId") long historyId, @Param("rootType") BusinessChangeRecordRecordType rootType,
        @Param("rootId") String rootId, @Param("now") Instant now);

    /** 根 CAS：current_version 等于 expectedVersion 时加 1，返回影响行数。 */
    int advanceRoot(@Param("historyId") long historyId, @Param("expectedVersion") long expectedVersion, @Param("now") Instant now);
}
