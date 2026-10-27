package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.mapper.ScreeningHitMapper;
import com.paytech.compliance.domainservice.repository.ScreeningHitRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 筛查命中持久化。 */
@Repository
@AllArgsConstructor
public class ScreeningHitRepositoryImpl implements ScreeningHitRepository {
    private final ScreeningHitMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(ScreeningHit row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_screening_hit 写入行数异常");
        }
    }

    /** 若干执行的命中。 */
    @Override
    public List<ScreeningHit> findByRunIds(List<Long> screeningRunIds) {
        return screeningRunIds.isEmpty() ? List.of() : mapper.findByRunIds(screeningRunIds);
    }

    /** 以版本与 OPEN 为条件关闭命中，成功后同步原对象。 */
    @Override
    public void resolve(ScreeningHit row, Instant now) {
        if (mapper.resolve(row, now) != 1) {
            throw new ConcurrentRequestException("命中已被其他操作处置，请刷新后重试");
        }
        row.setStatus("RESOLVED");
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }

    /** 以版本与 OPEN 为条件递增命中版本（不关闭的处置），成功后同步原对象。 */
    @Override
    public void touch(ScreeningHit row, Instant now) {
        if (mapper.touch(row, now) != 1) {
            throw new ConcurrentRequestException("命中已被其他操作处置，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }

    /** 取代旧命中。 */
    @Override
    public int supersedeOthers(long caseSubjectId, long programScreeningId, long keepRunId, Instant now) {
        return mapper.supersedeOthers(caseSubjectId, programScreeningId, keepRunId, now);
    }
}
