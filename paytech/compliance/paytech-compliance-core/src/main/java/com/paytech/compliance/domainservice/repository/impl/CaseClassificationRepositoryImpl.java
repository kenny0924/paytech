package com.paytech.risk.compliance.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.compliance.core.dal.dataobject.CaseClassification;
import com.paytech.risk.compliance.core.dal.mapper.CaseClassificationMapper;
import com.paytech.risk.compliance.domainservice.repository.CaseClassificationRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_case_classification 单表持久化。 */
@Repository
@AllArgsConstructor
public class CaseClassificationRepositoryImpl implements CaseClassificationRepository {
    private final CaseClassificationMapper mapper;

    /** 新增任务经营分类。 */
    @Override
    public void insert(CaseClassification row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("任务经营分类写入行数异常");
        }
    }

    /** 案件的全部分类。 */
    @Override
    public List<CaseClassification> findByCase(long caseId) {
        return mapper.findByCase(caseId);
    }
}
