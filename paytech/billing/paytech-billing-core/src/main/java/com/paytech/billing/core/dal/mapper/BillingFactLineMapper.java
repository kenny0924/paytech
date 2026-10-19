package com.paytech.billing.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BillingFactLine;

/** t_billing_fact_line：计费事实明细行的登记与读取。 */
@Mapper
public interface BillingFactLineMapper {

    /** 一次插入一条事实的全部明细行；返回影响行数。 */
    int insertAll(@Param("lines") List<BillingFactLine> lines);

    /** 一条事实的全部明细行，按行号升序。 */
    List<BillingFactLine> selectByFactId(@Param("factId") long factId);

    /** 一组事实的全部明细行，按事实与行号升序。 */
    List<BillingFactLine> selectByFactIds(@Param("factIds") Collection<Long> factIds);
}
