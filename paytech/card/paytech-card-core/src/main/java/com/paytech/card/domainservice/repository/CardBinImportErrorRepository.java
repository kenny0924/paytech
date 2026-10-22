package com.paytech.card.domainservice.repository;

import java.util.List;

import com.paytech.card.core.dal.dataobject.CardBinImportError;

/** BIN 库导入错误明细数据访问 */
public interface CardBinImportErrorRepository {
    /** 批量插入同一版本的错误明细。 */
    void insertErrors(List<CardBinImportError> errors);

    /** 某版本的错误明细条数。 */
    long countErrors(long versionId);

    /** 某版本的错误明细分页，按行号排序。 */
    List<CardBinImportError> listErrors(long versionId, int offset, int limit);
}
