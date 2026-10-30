package com.paytech.tax.core.dal.mapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxPeriod;

/** t_tax_period：申报期间生成与读取。 */
@Mapper
public interface TaxPeriodMapper {
    /** 期间不存在时插入，已存在不变；返回影响行数（0 或 1）。 */
    int insertIfAbsent(@Param("row") TaxPeriod row);

    /** 按注册与起止日读取（uk_tax_period）；不存在返回 null。 */
    TaxPeriod selectByRange(@Param("registrationId") long registrationId, @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    /** 按 ID 批量读取。 */
    List<TaxPeriod> selectByIds(@Param("ids") Collection<Long> ids);
}
