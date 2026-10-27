package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;

/** 命中处置持久化（t_screening_resolution）。 */
public interface ScreeningResolutionRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(ScreeningResolution row);

    /** 若干命中的处置记录；空集合返回空列表。 */
    List<ScreeningResolution> findByHitIds(List<Long> hitIds);
}
