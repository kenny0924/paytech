package com.paytech.compliance.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;

/** t_qualification_decision 的资格决定持久化。 */
public interface QualificationDecisionRepository {
    /** 新增资格决定；影响行数不为 1 时失败。 */
    void insert(QualificationDecision row);

    /** 某 Client 入驻案件的最终决定；不存在返回 null。 */
    QualificationDecision findByCase(long caseId);

    /** 按决定 ID 读取；不存在返回 null。 */
    QualificationDecision findById(long decisionId);

    /** 按决定 ID 集合读取；空集合返回空列表。 */
    List<QualificationDecision> findByIds(Collection<Long> decisionIds);
}
