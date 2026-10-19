package com.paytech.commercial.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.mapper.BusinessChangeRecordMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 BusinessChangeRecordMapper 读写 t_business_change_record。 */
@AllArgsConstructor
public class BusinessChangeRecordRepositoryImpl implements BusinessChangeRecordRepository {
    private final BusinessChangeRecordMapper mapper;

    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    @Override
    public List<CheckoutRecords.BusinessChangeRecord> findCheckoutRoots(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findCheckoutRoots(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 读取一个聚合根的 Root 当前修订版本；Root 不存在为 null。 */
    @Override
    public Long findRootVersion(BusinessChangeRecordAggregateRootType rootType, String rootId) {
        return mapper.findRootVersion(rootType, rootId);
    }

    /** 插入一条历史身份，时间截断到微秒后写回对象。 */
    @Override
    public void insert(BusinessChangeRecord record) {
        record.setCreateTime(PersistenceTimes.micros(record.getCreateTime()));
        record.setUpdateTime(PersistenceTimes.micros(record.getUpdateTime()));
        if (mapper.insert(record) != 1) {
            throw new ErrorException("业务变更记录插入失败: " + record.getRecordType().getCode() + " " + record.getRecordId());
        }
    }

    /** 按唯一键读取历史身份。 */
    @Override
    public BusinessChangeRecord findByRecord(BusinessChangeRecordRecordType recordType, String recordId) {
        return mapper.findByRecord(recordType, recordId);
    }

    /** 读取一组同类型 Root；空集合不访问数据库。 */
    @Override
    public List<BusinessChangeRecord> findRoots(BusinessChangeRecordAggregateRootType rootType,
        Collection<String> rootIds) {
        if (rootIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.findRoots(rootType, rootIds));
    }

    /** Root CAS 推进一次，未命中为并发冲突；成功后同步原对象。 */
    @Override
    public void advanceRoot(BusinessChangeRecord root, long expectedVersion, Instant now) {
        Instant time = PersistenceTimes.micros(now);
        if (mapper.advanceRoot(root.getHistoryId(), expectedVersion, expectedVersion + 1, time) != 1) {
            throw new ConcurrentRequestException("业务版本已变化，请刷新后重试: " + root.getRecordType().getCode() + " "
                + root.getRecordId() + "，期望版本 " + expectedVersion);
        }
        root.setCurrentVersion(expectedVersion + 1);
        root.setUpdateTime(time);
    }

    /** 推进 Child 指针；Root CAS 已串行化同聚合写入，未命中说明数据不一致。 */
    @Override
    public void advanceChild(BusinessChangeRecord child, long nextVersion, Instant now) {
        Instant time = PersistenceTimes.micros(now);
        if (mapper.advanceChild(child.getHistoryId(), nextVersion, time) != 1) {
            throw new ErrorException("业务变更 Child 版本指针推进失败: " + child.getRecordType().getCode() + " "
                + child.getRecordId());
        }
        child.setCurrentVersion(nextVersion);
        child.setUpdateTime(time);
    }
}
