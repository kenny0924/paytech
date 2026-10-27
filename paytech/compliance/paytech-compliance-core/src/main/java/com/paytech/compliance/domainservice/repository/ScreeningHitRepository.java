package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ScreeningHit;

/** 筛查命中持久化（t_screening_hit）。 */
public interface ScreeningHitRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(ScreeningHit row);

    /** 若干执行的命中；空集合返回空列表。 */
    List<ScreeningHit> findByRunIds(List<Long> screeningRunIds);

    /** 以版本与 OPEN 为条件关闭命中；条件不满足时并发冲突，原对象不变。 */
    void resolve(ScreeningHit row, Instant now);

    /** 以版本与 OPEN 为条件递增命中版本（不关闭的处置）；条件不满足时并发冲突，原对象不变。 */
    void touch(ScreeningHit row, Instant now);

    /** 新执行成功后取代同一主体同一筛查模板其他执行的命中，返回影响行数。 */
    int supersedeOthers(long caseSubjectId, long programScreeningId, long keepRunId, Instant now);
}
