package com.paytech.risk.compliance.domainservice.repository;

import java.util.List;
import com.paytech.risk.compliance.core.dal.dataobject.CaseDependency;

/** t_dd_case_dependency 的案件依赖持久化。 */
public interface CaseDependencyRepository {
    /** 新增案件依赖；影响行数不为 1 时失败。 */
    void insert(CaseDependency row);

    /** 主案件的全部依赖。 */
    List<CaseDependency> findByParent(long parentCaseId);
}
