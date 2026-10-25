package com.paytech.client.master.domainservice.repository.impl;

import static com.paytech.client.master.api.enums.EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.BusinessChangeRecord;
import com.paytech.client.master.core.dal.mapper.BusinessChangeRecordMapper;
import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeRecordMapper 读写 t_business_change_record。 */
@AllArgsConstructor
public class BusinessChangeRecordRepositoryImpl implements BusinessChangeRecordRepository {
    private final BusinessChangeRecordMapper mapper;

    /** 读取影响本 Client Checkout 候选的业务根修订。 */
    @Override
    public List<BusinessChangeRecord> findCheckoutRoots(long clientId) {
        return List.copyOf(mapper.findCheckoutRoots(clientId));
    }

    /** 读取一个业务根；不存在时返回 null。 */
    @Override
    public BusinessChangeRecord findRoot(BusinessChangeRecordRecordType rootType, String rootId) {
        return mapper.findRoot(rootType, rootId);
    }

    /** 补建 V1 根。 */
    @Override
    public void insertRoot(long historyId, BusinessChangeRecordRecordType rootType, String rootId, Instant now) {
        mapper.insertRoot(historyId, rootType, rootId, now);
    }

    /** 影响行数不为 1 即版本冲突。 */
    @Override
    public void advanceRoot(long historyId, long expectedVersion, Instant now) {
        if (mapper.advanceRoot(historyId, expectedVersion, now) != 1) {
            throw new FailException(ENTITY_CHANGE_VERSION_CONFLICT);
        }
    }
}
