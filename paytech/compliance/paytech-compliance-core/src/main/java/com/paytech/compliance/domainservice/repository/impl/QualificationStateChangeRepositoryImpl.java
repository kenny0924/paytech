package com.paytech.compliance.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.core.dal.mapper.QualificationStateChangeMapper;
import com.paytech.compliance.domainservice.repository.QualificationStateChangeRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_qualification_state_change 单表持久化。 */
@Repository
@AllArgsConstructor
public class QualificationStateChangeRepositoryImpl implements QualificationStateChangeRepository {
    private final QualificationStateChangeMapper mapper;

    /** 追加一条状态变更。 */
    @Override
    public void insert(QualificationStateChange row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("资格状态变更写入行数异常");
        }
    }

    /** 某资格指定聚合版本的状态变更。 */
    @Override
    public QualificationStateChange findByVersion(long qualificationId, long version) {
        return mapper.findByVersion(qualificationId, version);
    }

    /** 某资格的全部状态变更。 */
    @Override
    public List<QualificationStateChange> findByQualification(long qualificationId) {
        return mapper.findByQualification(qualificationId);
    }
}
