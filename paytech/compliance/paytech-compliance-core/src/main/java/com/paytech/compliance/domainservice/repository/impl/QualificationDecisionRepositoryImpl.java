package com.paytech.compliance.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.mapper.QualificationDecisionMapper;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_qualification_decision 单表持久化。 */
@Repository
@AllArgsConstructor
public class QualificationDecisionRepositoryImpl implements QualificationDecisionRepository {
    private final QualificationDecisionMapper mapper;

    /** 新增资格决定。 */
    @Override
    public void insert(QualificationDecision row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("资格决定写入行数异常");
        }
    }

    /** 某 Client 入驻案件的最终决定。 */
    @Override
    public QualificationDecision findByCase(long caseId) {
        return mapper.findByCase(caseId);
    }

    /** 按决定 ID 集合读取。 */
    @Override
    public List<QualificationDecision> findByIds(Collection<Long> decisionIds) {
        return decisionIds.isEmpty() ? List.of() : mapper.findByIds(decisionIds);
    }
}
