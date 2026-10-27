package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;

/** t_dd_check 的 SQL；业务 SQL 位于 XML。 */
public interface CaseCheckMapper {
    /** 新增一行。 */
    int insert(@Param("row") CaseCheck row);

    /** 若干案件的检查 */
    List<CaseCheck> findByCaseIds(@Param("caseIds") List<Long> caseIds);

    /** 以版本与 REQUESTED 为条件录入人工检查结论 */
    int record(@Param("row") CaseCheck row, @Param("outcome") String outcome, @Param("reasonCode") String reasonCode, @Param("evidenceRef") String evidenceRef, @Param("note") String note, @Param("reviewerId") long reviewerId, @Param("validUntil") Instant validUntil, @Param("operatedBy") String operatedBy, @Param("onBehalfOfPartnerId") Long onBehalfOfPartnerId, @Param("now") Instant now);
}
