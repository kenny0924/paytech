package com.paytech.billing.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;

/** t_billing_fact_line_link：明细行链接读取（本期无生产写入）。 */
@Mapper
public interface BillingFactLineLinkMapper {

    /** 以给定明细行为原行的链接，按 ID 升序。 */
    List<BillingFactLineLink> selectBySourceLineIds(@Param("lineIds") Collection<Long> lineIds);

    /** 以给定明细行为新行的链接，按 ID 升序。 */
    List<BillingFactLineLink> selectByTargetLineIds(@Param("lineIds") Collection<Long> lineIds);
}
