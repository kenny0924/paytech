package com.paytech.card.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.card.core.dal.dataobject.CardBinImportError;

/** BIN 库导入错误明细 SQL 映射 */
@Mapper
public interface CardBinImportErrorMapper {
    /** 批量插入同一版本的错误明细。 */
    int insertErrors(@Param("errors") List<CardBinImportError> errors);

    /** 某版本的错误明细条数。 */
    long countErrors(@Param("versionId") long versionId);

    /** 某版本的错误明细分页，按行号排序。 */
    List<CardBinImportError> listErrors(@Param("versionId") long versionId, @Param("offset") int offset,
        @Param("limit") int limit);
}
