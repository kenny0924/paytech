package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ScreeningRun;

/** t_screening_run 的 SQL；业务 SQL 位于 XML。 */
public interface ScreeningRunMapper {
    /** 新增一行。 */
    int insert(@Param("row") ScreeningRun row);

    /** 若干案件的筛查执行，最新在前 */
    List<ScreeningRun> findByCaseIds(@Param("caseIds") List<Long> caseIds);

    /** 以版本与 STARTED 为条件结束一次执行 */
    int complete(@Param("row") ScreeningRun row, @Param("status") String status, @Param("executionRef") String executionRef, @Param("now") Instant now);
}
