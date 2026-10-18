package com.paytech.compliance.domainservice.repository;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;

/** t_dd_case_state_change 的案件状态变更持久化。 */
public interface CaseStateChangeRepository {
    /** 新增案件状态变更；影响行数不为 1 时失败。 */
    void insert(CaseStateChange row);
}
