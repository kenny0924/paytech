package com.paytech.risk.compliance.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import java.util.List;
import com.paytech.risk.compliance.core.dal.dataobject.CaseDependency;
import com.paytech.risk.compliance.core.dal.mapper.CaseDependencyMapper;
import com.paytech.risk.compliance.domainservice.repository.CaseDependencyRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_case_dependency 单表持久化。 */
@Repository
@AllArgsConstructor
public class CaseDependencyRepositoryImpl implements CaseDependencyRepository {
    private final CaseDependencyMapper mapper;

    /** 新增案件依赖。 */
    @Override
    public void insert(CaseDependency row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("案件依赖写入行数异常");
        }
    }

    /** 主案件的全部依赖。 */
    @Override
    public List<CaseDependency> findByParent(long parentCaseId) {
        return mapper.findByParent(parentCaseId);
    }
}
