package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ScreeningHitList;
import com.paytech.compliance.core.dal.mapper.ScreeningHitListMapper;
import com.paytech.compliance.domainservice.repository.ScreeningHitListRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 命中名单类型持久化。 */
@Repository
@AllArgsConstructor
public class ScreeningHitListRepositoryImpl implements ScreeningHitListRepository {
    private final ScreeningHitListMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(ScreeningHitList row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_screening_hit_list 写入行数异常");
        }
    }

    /** 若干命中的名单类型。 */
    @Override
    public List<ScreeningHitList> findByHitIds(List<Long> hitIds) {
        return hitIds.isEmpty() ? List.of() : mapper.findByHitIds(hitIds);
    }
}
