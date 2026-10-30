package com.paytech.tax.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxCalculation;

/** t_tax_calculation：计算头写入与读取。 */
@Mapper
public interface TaxCalculationMapper {
    /** 插入一行；返回影响行数。 */
    int insert(@Param("row") TaxCalculation row);

    /** 按 ID 读取；不存在返回 null。 */
    TaxCalculation selectById(@Param("id") long id);

    /** 按计算身份读取（uk_tax_calculation_revision）；不存在返回 null。 */
    TaxCalculation selectByIdentity(@Param("sourceSystem") String sourceSystem, @Param("sourceObjectType") String sourceObjectType, @Param("sourceObjectId") long sourceObjectId, @Param("sourceRevision") int sourceRevision, @Param("taxScopeKey") String taxScopeKey);

    /** 来源对象编号下的全部计算，按准备版本升序。 */
    List<TaxCalculation> selectByObjectNo(@Param("sourceObjectNo") String sourceObjectNo);
}
