package com.paytech.tax.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.core.dal.mapper.TaxDocumentMapper;
import com.paytech.tax.domainservice.repository.TaxDocumentRepository;

import lombok.AllArgsConstructor;

/** TaxDocumentRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxDocumentRepositoryImpl implements TaxDocumentRepository {
    private final TaxDocumentMapper mapper;

    /** 插入一行；影响行数不为 1 时为系统异常。 */
    @Override
    public void insert(TaxDocument row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_tax_document 插入行数不符: " + row.getId());
        }
    }

    /** 按单据身份读取（uk_tax_document）；不存在返回 null。 */
    @Override
    public TaxDocument findByTransaction(long transactionId, TaxDocumentKind documentKind, String documentScopeKey) {
        return mapper.selectByTransaction(transactionId, documentKind, documentScopeKey);
    }

    /** RESERVED 单据写文件地址与摘要并转 ISSUED；条件不满足为并发冲突，成功后同步传入对象。 */
    @Override
    public void markIssued(TaxDocument document, String fileAddress, String digest, Instant now) {
        if (mapper.updateIssued(document.getId(), fileAddress, digest, now) != 1) {
            throw new ConcurrentRequestException("税务单据已不是 RESERVED: " + document.getId());
        }
        document.setDocumentFileAddress(fileAddress);
        document.setDocumentDigest(digest);
        document.setIssueStatus(TaxDocumentIssueStatus.ISSUED);
        document.setUpdateTime(now);
    }

    /** 一组交易的全部单据。 */
    @Override
    public List<TaxDocument> findByTransactionIds(Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByTransactionIds(transactionIds);
    }
}
