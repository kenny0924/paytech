package com.paytech.compliance.domainservice.repository;

import java.util.List;
import com.paytech.compliance.core.dal.dataobject.CaseSubject;

/** t_dd_case_subject 的案件主体持久化。 */
public interface CaseSubjectRepository {
    /** 新增案件主体；影响行数不为 1 时失败。 */
    void insert(CaseSubject row);

    /** 若干案件的全部主体；空集合返回空列表。 */
    List<CaseSubject> findByCaseIds(List<Long> caseIds);
}
