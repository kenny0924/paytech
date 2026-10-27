package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.compliance.domain.ReviewRequirementView;

/** t_dd_requirement 的案件材料要求持久化。 */
public interface CaseRequirementRepository {
    /** 新增案件材料要求；影响行数不为 1 时失败。 */
    void insert(CaseRequirement row);

    /** 若干案件的材料要求及其程序模板；空集合返回空列表；允许类型不在本查询中。 */
    List<ReviewRequirementView> findViewsByCaseIds(List<Long> caseIds);

    /** 以版本为条件更新要求状态，成功后同步原视图；已变化时并发冲突。 */
    void updateStatus(ReviewRequirementView requirement, String status, Instant now);

    /** 以版本为条件豁免要求，成功后同步原视图；已接受或已变化时并发冲突。 */
    void waive(ReviewRequirementView requirement, long actorId, String reasonCode, String note, Instant now);
}
