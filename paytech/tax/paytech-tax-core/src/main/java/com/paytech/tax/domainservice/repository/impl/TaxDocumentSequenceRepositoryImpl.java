package com.paytech.tax.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.tax.core.dal.dataobject.TaxDocumentSequence;
import com.paytech.tax.core.dal.mapper.TaxDocumentSequenceMapper;
import com.paytech.tax.domainservice.repository.TaxDocumentSequenceRepository;

import lombok.AllArgsConstructor;

/** TaxDocumentSequenceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxDocumentSequenceRepositoryImpl implements TaxDocumentSequenceRepository {
    private final TaxDocumentSequenceMapper mapper;

    /** 序列不存在时插入，已存在不变；必须在调用方事务内随后加锁重读。 */
    @Override
    public void insertIfAbsent(TaxDocumentSequence row) {
        mapper.insertIfAbsent(row);
    }

    /** 在调用方事务内锁定序列行（uk_tax_document_sequence）；不存在返回 null。 */
    @Override
    public TaxDocumentSequence findForUpdate(long issuerPartyId, long registrationId, String seriesCode) {
        return mapper.selectForUpdate(issuerPartyId, registrationId, seriesCode);
    }

    /** 在已加锁的序列行上分配当前号码并递增；条件不满足为并发冲突。 */
    @Override
    public long allocate(TaxDocumentSequence sequence, Instant now) {
        long number = sequence.getNextNumber();
        if (mapper.updateNextNumber(sequence.getId(), number, now) != 1) {
            throw new ConcurrentRequestException("单据号码序列已变化: " + sequence.getSeriesCode());
        }
        sequence.setNextNumber(number + 1);
        sequence.setUpdateTime(now);
        return number;
    }
}
