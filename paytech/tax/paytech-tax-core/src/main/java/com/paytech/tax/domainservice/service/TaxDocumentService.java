package com.paytech.tax.domainservice.service;

import java.time.Instant;

import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.domain.document.TaxDocumentIssueContext;

/** 简化税票的分号与开具状态（technique 9.3）；只访问本库。 */
public interface TaxDocumentService {

    /**
     * 读取开票所需的已登记事实
     *
     * @param transactionId 正式交易 ID
     * @return 上下文；交易不存在或没有可开票的应税组件返回 null
     */
    TaxDocumentIssueContext loadIssueContext(long transactionId);

    /**
     * 短事务：锁定号码序列（年度序列首次使用时建立）、取号并插入 RESERVED 单据
     *
     * @param context 开票上下文（document 为空）
     * @param now 分号时间
     * @return RESERVED 单据
     * @throws org.springframework.dao.DuplicateKeyException 同一交易已被并发分号（事务已回滚，号码未占用）
     */
    TaxDocument reserveReceipt(TaxDocumentIssueContext context, Instant now);

    /**
     * 短事务：写文件地址与摘要并转 ISSUED
     *
     * @param document RESERVED 单据，成功后同步
     * @param fileAddress /files/v1/{fileId}
     * @param digest 文件 SHA-256 小写十六进制
     * @param now 更新时间
     */
    void markIssued(TaxDocument document, String fileAddress, String digest, Instant now);
}
