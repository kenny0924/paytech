package com.paytech.tax.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.core.dal.dataobject.TaxDocument;

/** t_tax_document 仓储：税务单据写入、开具与读取。 */
public interface TaxDocumentRepository {
    /** 插入一行；影响行数不为 1 时为系统异常。 */
    void insert(TaxDocument row);

    /** 按单据身份读取（uk_tax_document）；不存在返回 null。 */
    TaxDocument findByTransaction(long transactionId, TaxDocumentKind documentKind, String documentScopeKey);

    /** RESERVED 单据写文件地址与摘要并转 ISSUED；条件不满足为并发冲突，成功后同步传入对象。 */
    void markIssued(TaxDocument document, String fileAddress, String digest, Instant now);

    /** 一组交易的全部单据。 */
    List<TaxDocument> findByTransactionIds(Collection<Long> transactionIds);
}
