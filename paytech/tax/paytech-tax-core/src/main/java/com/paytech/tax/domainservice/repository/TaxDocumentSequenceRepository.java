package com.paytech.tax.domainservice.repository;

import java.time.Instant;

import com.paytech.tax.core.dal.dataobject.TaxDocumentSequence;

/** t_tax_document_sequence 仓储：单据号码序列。 */
public interface TaxDocumentSequenceRepository {
    /** 序列不存在时插入，已存在不变；必须在调用方事务内随后加锁重读。 */
    void insertIfAbsent(TaxDocumentSequence row);

    /** 在调用方事务内锁定序列行（uk_tax_document_sequence）；不存在返回 null。 */
    TaxDocumentSequence findForUpdate(long issuerPartyId, long registrationId, String seriesCode);

    /** 在已加锁的序列行上分配当前号码并递增；条件不满足为并发冲突。 */
    long allocate(TaxDocumentSequence sequence, Instant now);
}
