package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxTransaction;

/** t_tax_transaction：正式交易写入与读取。 */
@Mapper
public interface TaxTransactionMapper {
    /** 插入一行；返回影响行数。 */
    int insert(@Param("row") TaxTransaction row);

    /** 按源事实身份读取（uk_tax_transaction_source）；不存在返回 null。 */
    TaxTransaction selectBySource(@Param("sourceSystem") String sourceSystem, @Param("sourceFactType") String sourceFactType, @Param("sourceFactId") String sourceFactId, @Param("taxScopeKey") String taxScopeKey);

    /** 按 ID 读取；不存在返回 null。 */
    TaxTransaction selectById(@Param("id") long id);

    /** 一组计算对应的正式交易。 */
    List<TaxTransaction> selectByCalculationIds(@Param("calculationIds") Collection<Long> calculationIds);
}
