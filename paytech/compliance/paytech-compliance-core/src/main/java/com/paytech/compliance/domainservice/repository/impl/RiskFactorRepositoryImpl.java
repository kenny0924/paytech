package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.RiskFactor;
import com.paytech.compliance.core.dal.mapper.RiskFactorMapper;
import com.paytech.compliance.domainservice.repository.RiskFactorRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 风险因子持久化。 */
@Repository
@AllArgsConstructor
public class RiskFactorRepositoryImpl implements RiskFactorRepository {
    private final RiskFactorMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(RiskFactor row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_dd_risk_factor 写入行数异常");
        }
    }

    /** 主案件的风险因子。 */
    @Override
    public List<RiskFactor> findByCase(long caseId) {
        return mapper.findByCase(caseId);
    }

    /** 删除主案件某来源的全部因子。 */
    @Override
    public int deleteByCaseAndSource(long caseId, String source) {
        return mapper.deleteByCaseAndSource(caseId, source);
    }
}
