package com.paytech.billing.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;

/** t_billing_fact_ref：计费事实外部引用的登记与读取。 */
@Mapper
public interface BillingFactRefMapper {

    /** 一次插入一条事实的全部引用；返回影响行数。 */
    int batchInsert(@Param("refs") List<BillingFactRef> refs);

    /** 一条事实的全部引用，按引用类型升序。 */
    List<BillingFactRef> selectByFactId(@Param("factId") long factId);

    /** 一组事实的全部引用，按事实与引用类型升序。 */
    List<BillingFactRef> selectByFactIds(@Param("factIds") Collection<Long> factIds);

    /** 引用给定外部对象且 ID 大于游标的事实 ID，按 ID 升序取 limit 条。 */
    List<Long> selectFactIdsByRef(@Param("refType") FactRefType refType, @Param("refId") String refId,
        @Param("fromFactId") long fromFactId, @Param("limit") int limit);
}
