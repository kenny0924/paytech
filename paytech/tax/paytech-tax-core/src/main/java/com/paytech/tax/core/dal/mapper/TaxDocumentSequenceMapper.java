package com.paytech.tax.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxDocumentSequence;

/** t_tax_document_sequence：单据号码序列。 */
@Mapper
public interface TaxDocumentSequenceMapper {
    /** 序列不存在时插入，已存在不变；返回影响行数。 */
    int insertIfAbsent(@Param("row") TaxDocumentSequence row);

    /** 在调用方事务内锁定序列行（uk_tax_document_sequence）；不存在返回 null。 */
    TaxDocumentSequence selectForUpdate(@Param("issuerPartyId") long issuerPartyId, @Param("registrationId") long registrationId, @Param("seriesCode") String seriesCode);

    /** 以期望值递增下一号码；返回影响行数。 */
    int updateNextNumber(@Param("id") long id, @Param("expected") long expected, @Param("now") Instant now);
}
