package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.CaseRequirement;
import com.paytech.compliance.core.dal.mapper.CaseRequirementMapper;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.repository.CaseRequirementRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 案件材料要求持久化。 */
@Repository
@AllArgsConstructor
public class CaseRequirementRepositoryImpl implements CaseRequirementRepository {
    private final CaseRequirementMapper mapper;

    /** 新增要求。 */
    @Override
    public void insert(CaseRequirement row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("材料要求写入行数异常");
        }
    }

    /** 组合读取。 */
    @Override
    public List<ReviewRequirementView> findViewsByCaseIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findViewsByCaseIds(caseIds);
    }

    /** 条件更新状态。 */
    @Override
    public void updateStatus(ReviewRequirementView requirement, String status, Instant now) {
        if (mapper.updateStatus(requirement.getRequirementId(), requirement.getVersion(), status, now) != 1) {
            throw new ConcurrentRequestException("材料要求已变化，请刷新后重试");
        }
        requirement.setStatus(status);
        requirement.setVersion(requirement.getVersion() + 1);
    }

    /** 条件豁免。 */
    @Override
    public void waive(ReviewRequirementView requirement, long actorId, String reasonCode, String note, Instant now) {
        if (mapper.waive(requirement.getRequirementId(), requirement.getVersion(), actorId, reasonCode, note, now) != 1) {
            throw new ConcurrentRequestException("材料要求已变化，请刷新后重试");
        }
        requirement.setStatus(ReviewCodes.WAIVED);
        requirement.setWaivedBy(actorId);
        requirement.setWaivedReasonCode(reasonCode);
        requirement.setWaivedTime(now);
        requirement.setVersion(requirement.getVersion() + 1);
    }
}
