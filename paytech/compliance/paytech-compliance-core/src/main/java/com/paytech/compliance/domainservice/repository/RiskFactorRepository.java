package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.RiskFactor;

/** 风险因子持久化（t_dd_risk_factor）。 */
public interface RiskFactorRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(RiskFactor row);

    /** 主案件的风险因子；空集合返回空列表。 */
    List<RiskFactor> findByCase(long caseId);

    /** 删除主案件某来源的全部因子（评级替换），返回删除行数。 */
    int deleteByCaseAndSource(long caseId, String source);
}
