package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ScreeningHit;

/** t_screening_hit 的 SQL；业务 SQL 位于 XML。 */
public interface ScreeningHitMapper {
    /** 新增一行。 */
    int insert(@Param("row") ScreeningHit row);

    /** 若干执行的命中 */
    List<ScreeningHit> findByRunIds(@Param("screeningRunIds") List<Long> screeningRunIds);

    /** 以版本与 OPEN 为条件关闭命中 */
    int resolve(@Param("row") ScreeningHit row, @Param("now") Instant now);

    /** 以版本与 OPEN 为条件递增命中版本（不关闭的处置） */
    int touch(@Param("row") ScreeningHit row, @Param("now") Instant now);

    /** 把同一主体同一筛查模板其他执行的 OPEN / RESOLVED 命中置为 SUPERSEDED，返回影响行数。 */
    int supersedeOthers(@Param("caseSubjectId") long caseSubjectId, @Param("programScreeningId") long programScreeningId,
        @Param("keepRunId") long keepRunId, @Param("now") Instant now);
}
