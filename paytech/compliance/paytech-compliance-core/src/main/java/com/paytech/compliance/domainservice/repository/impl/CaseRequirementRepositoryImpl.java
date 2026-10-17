package com.paytech.compliance.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import com.paytech.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.compliance.core.dal.mapper.CaseRequirementMapper;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_dd_requirement 单表持久化。 */
@Repository
@AllArgsConstructor
public class CaseRequirementRepositoryImpl implements CaseRequirementRepository {
    private final CaseRequirementMapper mapper;

    /** 新增案件材料要求。 */
    @Override
    public void insert(CaseRequirement row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("案件材料要求写入行数异常");
        }
    }

    /** 若干案件的材料要求及其程序模板。 */
    @Override
    public List<com.paytech.compliance.domain.ReviewRequirementView> findViewsByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findViewsByCaseIds(caseIds);
    }

    /** 条件更新要求状态并同步。 */
    @Override
    public void updateStatus(com.paytech.compliance.domain.ReviewRequirementView requirement, String status, Instant now) {
        if (mapper.updateStatus(requirement.getRequirementId(), requirement.getVersion(), status, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("材料要求已变化，请刷新后重试");
        }
        requirement.setStatus(status);
        requirement.setVersion(requirement.getVersion() + 1);
    }
}
