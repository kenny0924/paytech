package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ReturnError;

/** t_dd_return_error 的 SQL；业务 SQL 位于 XML。 */
public interface ReturnErrorMapper {
    /** 新增一行。 */
    int insert(@Param("row") ReturnError row);

    /** 主案件的退回错误 */
    List<ReturnError> findByCase(@Param("caseId") long caseId);
}
