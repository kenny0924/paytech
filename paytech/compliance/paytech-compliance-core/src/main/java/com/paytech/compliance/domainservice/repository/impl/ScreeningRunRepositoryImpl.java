package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ScreeningRun;
import com.paytech.compliance.core.dal.mapper.ScreeningRunMapper;
import com.paytech.compliance.domainservice.repository.ScreeningRunRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 筛查执行持久化。 */
@Repository
@AllArgsConstructor
public class ScreeningRunRepositoryImpl implements ScreeningRunRepository {
    private final ScreeningRunMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(ScreeningRun row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_screening_run 写入行数异常");
        }
    }

    /** 若干案件的筛查执行，最新在前。 */
    @Override
    public List<ScreeningRun> findByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByCaseIds(caseIds);
    }

    /** 以版本与 STARTED 为条件结束一次执行，成功后同步原对象。 */
    @Override
    public void complete(ScreeningRun row, String status, String executionRef, Instant now) {
        if (mapper.complete(row, status, executionRef, now) != 1) {
            throw new ConcurrentRequestException("筛查执行已结束");
        }
        row.setStatus(status);
        row.setExecutionRef(executionRef);
        row.setCompletedTime(now);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}
