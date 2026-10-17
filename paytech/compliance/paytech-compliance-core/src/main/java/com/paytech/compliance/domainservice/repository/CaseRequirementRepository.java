package com.paytech.risk.compliance.domainservice.repository;

import java.util.List;
import java.time.Instant;
import com.paytech.risk.compliance.core.dal.dataobject.CaseRequirement;

/** t_dd_requirement 的案件材料要求持久化。 */
public interface CaseRequirementRepository {
    /** 新增案件材料要求；影响行数不为 1 时失败。 */
    void insert(CaseRequirement row);

    /** 若干案件的材料要求及其程序模板；空集合返回空列表。 */
    List<com.paytech.risk.compliance.domain.ReviewRequirementView> findViewsByCaseIds(List<Long> caseIds);

    /** 以版本为条件更新要求状态，成功后同步原视图；已变化时并发冲突。 */
    void updateStatus(com.paytech.risk.compliance.domain.ReviewRequirementView requirement, String status, Instant now);
}
