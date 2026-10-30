package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;

/** t_tax_calculation_line：计算行写入与读取。 */
@Mapper
public interface TaxCalculationLineMapper {
    /** 一次插入多行；返回影响行数。 */
    int batchInsert(@Param("rows") List<TaxCalculationLine> rows);

    /** 一组计算的全部行，按计算与行号升序。 */
    List<TaxCalculationLine> selectByCalculationIds(@Param("calculationIds") Collection<Long> calculationIds);
}
