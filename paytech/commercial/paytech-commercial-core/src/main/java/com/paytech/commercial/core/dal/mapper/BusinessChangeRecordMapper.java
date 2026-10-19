package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;

/** t_business_change_record（commercial 实例）的持久化读写。 */
public interface BusinessChangeRecordMapper {
    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    List<CheckoutRecords.BusinessChangeRecord> findCheckoutRoots(@Param("clientId") long clientId,
        @Param("entityNos") List<String> entityNos, @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 读取一个聚合根的 Root 当前修订版本；Root 不存在为 null。 */
    Long findRootVersion(@Param("rootType") BusinessChangeRecordAggregateRootType rootType, @Param("rootId") String rootId);

    /** 插入一条历史身份。 */
    int insert(@Param("row") BusinessChangeRecord row);

    /** 按唯一键（record_type, record_id）读取完整行。 */
    BusinessChangeRecord findByRecord(@Param("recordType") BusinessChangeRecordRecordType recordType,
        @Param("recordId") String recordId);

    /** 读取一组同类型 Root 的完整行。 */
    List<BusinessChangeRecord> findRoots(@Param("rootType") BusinessChangeRecordAggregateRootType rootType,
        @Param("rootIds") Collection<String> rootIds);

    /** Root CAS：current_version 等于期望版本时推进到下一版本。 */
    int advanceRoot(@Param("historyId") long historyId, @Param("expectedVersion") long expectedVersion,
        @Param("nextVersion") long nextVersion, @Param("now") Instant now);

    /** Child 版本指针：只允许向前推进到 Root 的下一版本。 */
    int advanceChild(@Param("historyId") long historyId, @Param("nextVersion") long nextVersion,
        @Param("now") Instant now);
}
