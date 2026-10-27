package com.paytech.compliance.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.compliance.core.dal.dataobject.RiskFactor;

/** t_dd_risk_factor 的 SQL；业务 SQL 位于 XML。 */
public interface RiskFactorMapper {
    /** 新增一行。 */
    int insert(@Param("row") RiskFactor row);

    /** 主案件的风险因子 */
    List<RiskFactor> findByCase(@Param("caseId") long caseId);

    /** 删除主案件某来源的全部因子，返回删除行数。 */
    int deleteByCaseAndSource(@Param("caseId") long caseId, @Param("source") String source);
}
