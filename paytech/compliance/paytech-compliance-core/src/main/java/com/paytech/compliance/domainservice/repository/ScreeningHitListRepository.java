package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;

/** 命中名单类型持久化（t_screening_hit_list）。 */
public interface ScreeningHitListRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(ScreeningHitList row);

    /** 若干命中的名单类型；空集合返回空列表。 */
    List<ScreeningHitList> findByHitIds(List<Long> hitIds);
}
