package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;
import com.paytech.commercial.core.dal.mapper.TermVersionAcceptanceMapper;
import com.paytech.commercial.domainservice.repository.TermVersionAcceptanceRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 TermVersionAcceptanceMapper 访问 t_term_version_acceptance。 */
@AllArgsConstructor
public class TermVersionAcceptanceRepositoryImpl implements TermVersionAcceptanceRepository {
    private final TermVersionAcceptanceMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(TermVersionAcceptance acceptance) {
        acceptance.setAcceptedAt(micros(acceptance.getAcceptedAt()));
        acceptance.setCreateTime(micros(acceptance.getCreateTime()));
        acceptance.setUpdateTime(micros(acceptance.getUpdateTime()));
        if (mapper.insert(acceptance) != 1) {
            throw new ErrorException("价格签署记录插入行数不符: " + acceptance.getTermVersionId());
        }
    }

    /** 按签署编号读取。 */
    @Override
    public TermVersionAcceptance findByReference(String acceptanceReference) {
        return mapper.selectByReference(acceptanceReference);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<TermVersionAcceptance> findByVersionIds(Collection<Long> termVersionIds) {
        if (termVersionIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByVersionIds(termVersionIds));
    }
}
