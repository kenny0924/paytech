package com.paytech.risk.compliance.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import java.util.List;
import com.paytech.risk.compliance.core.dal.dataobject.CaseSubject;
import com.paytech.risk.compliance.core.dal.mapper.CaseSubjectMapper;
import com.paytech.risk.compliance.domainservice.repository.CaseSubjectRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_case_subject 单表持久化。 */
@Repository
@AllArgsConstructor
public class CaseSubjectRepositoryImpl implements CaseSubjectRepository {
    private final CaseSubjectMapper mapper;

    /** 新增案件主体。 */
    @Override
    public void insert(CaseSubject row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("案件主体写入行数异常");
        }
    }

    /** 若干案件的全部主体。 */
    @Override
    public List<CaseSubject> findByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByCaseIds(caseIds);
    }
}
