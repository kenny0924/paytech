package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;

/** t_screening_hit_list 的 SQL；业务 SQL 位于 XML。 */
public interface ScreeningHitListMapper {
    /** 新增一行。 */
    int insert(@Param("row") ScreeningHitList row);

    /** 若干命中的名单类型 */
    List<ScreeningHitList> findByHitIds(@Param("hitIds") List<Long> hitIds);
}
