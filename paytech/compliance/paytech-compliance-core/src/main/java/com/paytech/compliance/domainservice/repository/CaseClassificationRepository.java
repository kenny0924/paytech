package com.paytech.risk.compliance.domainservice.repository;

import java.util.List;

import com.paytech.risk.compliance.core.dal.dataobject.CaseClassification;

/** t_dd_case_classification 的任务经营分类持久化。 */
public interface CaseClassificationRepository {
    /** 新增任务经营分类；影响行数不为 1 时失败。 */
    void insert(CaseClassification row);

    /** 案件的全部分类。 */
    List<CaseClassification> findByCase(long caseId);
}
