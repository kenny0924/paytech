package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;

/** t_tax_calculation_component：计算组件写入与读取。 */
@Mapper
public interface TaxCalculationComponentMapper {
    /** 一次插入多行；返回影响行数。 */
    int batchInsert(@Param("rows") List<TaxCalculationComponent> rows);

    /** 一组计算的全部组件。 */
    List<TaxCalculationComponent> selectByCalculationIds(@Param("calculationIds") Collection<Long> calculationIds);
}
