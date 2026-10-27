package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;

/** t_screening_resolution 的 SQL；业务 SQL 位于 XML。 */
public interface ScreeningResolutionMapper {
    /** 新增一行。 */
    int insert(@Param("row") ScreeningResolution row);

    /** 若干命中的处置记录 */
    List<ScreeningResolution> findByHitIds(@Param("hitIds") List<Long> hitIds);
}
