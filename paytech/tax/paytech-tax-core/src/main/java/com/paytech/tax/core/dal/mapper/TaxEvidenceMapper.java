package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxEvidence;

/** t_tax_evidence：计算证据写入与读取。 */
@Mapper
public interface TaxEvidenceMapper {
    /** 一次插入多行；返回影响行数。 */
    int batchInsert(@Param("rows") List<TaxEvidence> rows);

    /** 一组计算的全部证据。 */
    List<TaxEvidence> selectByCalculationIds(@Param("calculationIds") Collection<Long> calculationIds);
}
