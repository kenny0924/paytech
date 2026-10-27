package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ScreeningRun;

/** 筛查执行持久化（t_screening_run）。 */
public interface ScreeningRunRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(ScreeningRun row);

    /** 若干案件的筛查执行，最新在前；空集合返回空列表。 */
    List<ScreeningRun> findByCaseIds(List<Long> caseIds);

    /** 以版本与 STARTED 为条件结束一次执行；条件不满足时并发冲突，原对象不变。 */
    void complete(ScreeningRun row, String status, String executionRef, Instant now);
}
