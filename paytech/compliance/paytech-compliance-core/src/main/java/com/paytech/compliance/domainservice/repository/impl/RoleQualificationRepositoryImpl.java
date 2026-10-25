package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.dal.mapper.RoleQualificationMapper;
import com.paytech.compliance.core.enums.QualificationErrorCode;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.domain.QualificationSubject;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** t_role_qualification 单表持久化。 */
@Repository
@AllArgsConstructor
public class RoleQualificationRepositoryImpl implements RoleQualificationRepository {
    private final RoleQualificationMapper mapper;

    /** 新增角色资格；唯一键冲突说明读取后已有其他命令建立同一资格。 */
    @Override
    public void insert(RoleQualification row) {
        int inserted;
        try {
            inserted = mapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            throw new FailException(QualificationErrorCode.QUALIFICATION_CHANGED,
                "角色资格已被其他操作建立: " + row.getRoleType() + "/" + row.getRoleId() + "/" + row.getQualificationType(), conflict);
        }
        if (inserted != 1) {
            throw new ErrorException("角色资格写入行数异常");
        }
    }

    /** 按资格 ID 读取。 */
    @Override
    public RoleQualification findById(long qualificationId) {
        return mapper.findById(qualificationId);
    }

    /** 按角色与资格类型读取。 */
    @Override
    public RoleQualification findByRole(String roleType, String roleId, String qualificationType) {
        return mapper.findByRole(roleType, roleId, qualificationType);
    }

    /** 若干主体的全部资格行。 */
    @Override
    public List<RoleQualification> findBySubjects(List<QualificationSubject> subjects) {
        return subjects.isEmpty() ? List.of() : mapper.findBySubjects(subjects);
    }

    /** 条件分页。 */
    @Override
    public List<RoleQualification> page(String roleType, String roleId, String qualificationType, String status, int offset,
        int size) {
        return mapper.page(roleType, roleId, qualificationType, status, offset, size);
    }

    /** 条件计数。 */
    @Override
    public long count(String roleType, String roleId, String qualificationType, String status) {
        return mapper.count(roleType, roleId, qualificationType, status);
    }

    /** 条件切换当前决定，成功后同步原对象。 */
    @Override
    public void applyDecision(RoleQualification row, QualificationDecision decision, Instant now) {
        String reviewStatus = QualificationStatus.ofCode(decision.getOutcome()).reviewStatus().getCode();
        if (mapper.applyDecision(row, decision.getDecisionId(), decision.getOutcome(), decision.getValidFrom(),
            decision.getValidTo(), reviewStatus, now) != 1) {
            throw new FailException(QualificationErrorCode.QUALIFICATION_CHANGED,
                "角色资格已被其他操作改变: " + row.getQualificationId() + ", 读取版本: " + row.getVersion());
        }
        row.setCurrentDecisionId(decision.getDecisionId());
        row.setStatus(decision.getOutcome());
        row.setValidFrom(decision.getValidFrom());
        row.setValidTo(decision.getValidTo());
        row.setReviewFrequencyCode(null);
        row.setNextReviewTime(null);
        row.setReviewStatus(reviewStatus);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}
