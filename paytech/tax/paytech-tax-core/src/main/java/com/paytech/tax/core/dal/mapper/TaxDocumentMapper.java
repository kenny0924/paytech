package com.paytech.tax.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.core.dal.dataobject.TaxDocument;

/** t_tax_document：税务单据写入、开具与读取。 */
@Mapper
public interface TaxDocumentMapper {
    /** 插入一行；返回影响行数。 */
    int insert(@Param("row") TaxDocument row);

    /** 按单据身份读取（uk_tax_document）；不存在返回 null。 */
    TaxDocument selectByTransaction(@Param("transactionId") long transactionId, @Param("documentKind") TaxDocumentKind documentKind, @Param("documentScopeKey") String documentScopeKey);

    /** RESERVED → ISSUED；返回影响行数。 */
    int updateIssued(@Param("id") long id, @Param("fileAddress") String fileAddress, @Param("digest") String digest,
        @Param("now") Instant now);

    /** 一组交易的全部单据。 */
    List<TaxDocument> selectByTransactionIds(@Param("transactionIds") Collection<Long> transactionIds);
}
