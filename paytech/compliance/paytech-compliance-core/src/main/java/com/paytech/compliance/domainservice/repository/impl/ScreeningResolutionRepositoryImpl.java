package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;
import com.paytech.compliance.core.dal.mapper.ScreeningResolutionMapper;
import com.paytech.compliance.domainservice.repository.ScreeningResolutionRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 命中处置持久化。 */
@Repository
@AllArgsConstructor
public class ScreeningResolutionRepositoryImpl implements ScreeningResolutionRepository {
    private final ScreeningResolutionMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(ScreeningResolution row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_screening_resolution 写入行数异常");
        }
    }

    /** 若干命中的处置记录。 */
    @Override
    public List<ScreeningResolution> findByHitIds(List<Long> hitIds) {
        return hitIds.isEmpty() ? List.of() : mapper.findByHitIds(hitIds);
    }
}
