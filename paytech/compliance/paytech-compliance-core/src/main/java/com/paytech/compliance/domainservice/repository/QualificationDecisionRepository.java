package com.paytech.compliance.domainservice.repository;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;

/** t_qualification_decision 的最终决定持久化。 */
public interface QualificationDecisionRepository {
    /** 新增最终决定；影响行数不为 1 时失败。 */
    void insert(QualificationDecision row);

    /** 某 Client 入驻案件的最终决定；不存在返回 null。 */
    QualificationDecision findByCase(long caseId);
}
