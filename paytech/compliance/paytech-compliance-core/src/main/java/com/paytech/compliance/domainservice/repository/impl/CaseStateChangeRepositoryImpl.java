package com.paytech.risk.compliance.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.risk.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.risk.compliance.core.dal.mapper.CaseStateChangeMapper;
import com.paytech.risk.compliance.domainservice.repository.CaseStateChangeRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_case_state_change 单表持久化。 */
@Repository
@AllArgsConstructor
public class CaseStateChangeRepositoryImpl implements CaseStateChangeRepository {
    private final CaseStateChangeMapper mapper;

    /** 新增案件状态变更。 */
    @Override
    public void insert(CaseStateChange row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("案件状态变更写入行数异常");
        }
    }
}
