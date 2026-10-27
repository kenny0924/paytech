package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_CHANGED;
import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_NOT_ACTIVE;
import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_NOT_FOUND;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.service.QualificationService;
import com.paytech.compliance.service.processor.request.RevokeQualificationRequest;
import com.paytech.compliance.service.processor.response.QualificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 运营撤销角色资格：只允许当前 APPROVED 的资格，结果为 REVOKED、复审状态 COMPLETED、有效结束时间为撤销时间
 * <li>资格不存在 404 QUALIFICATION_NOT_FOUND；当前不是 APPROVED 409 QUALIFICATION_NOT_ACTIVE；页面版本不一致或保存时版本已变化
 * 409 QUALIFICATION_CHANGED。</li>
 * <li>一个事务写 MANUAL 撤销决定、按版本条件切换投影、追加 OPERATIONS_MANUAL 状态变更；不使用资源锁。</li>
 */
@Component
@AllArgsConstructor
public class RevokeQualificationProcessor extends AbstractProcessor<RevokeQualificationRequest, QualificationResponse> {
    private final Clock clock;
    private final ComplianceIdentifierGenerator ids;
    private final QualificationService qualificationService;
    private final RoleQualificationRepository qualifications;

    @Override
    protected QualificationResponse processRequest(RevokeQualificationRequest request) {
        // 当前资格、状态与页面版本
        RoleQualification current = qualifications.findById(request.getQualificationId());
        if (current == null) {
            throw new FailException(QUALIFICATION_NOT_FOUND, "角色资格不存在: " + request.getQualificationId());
        }
        if (!QualificationStatus.APPROVED.getCode().equals(current.getStatus())) {
            throw new FailException(QUALIFICATION_NOT_ACTIVE,
                "只有已批准的资格可以撤销: " + current.getQualificationId() + ", 当前状态: " + current.getStatus());
        }
        if (current.getVersion() != request.getExpectedVersion()) {
            throw new FailException(QUALIFICATION_CHANGED, "角色资格已被其他操作改变: " + current.getQualificationId()
                + ", 页面版本: " + request.getExpectedVersion() + ", 当前版本: " + current.getVersion());
        }

        // 保存撤销决定与投影
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        QualificationChangeContext context = QualificationChangeBuilder.change(ids, decision(request, current, now), current,
            request.getReasonCode(), RecordManualQualificationProcessor.TRIGGER_TYPE, null);
        qualificationService.saveChange(context);

        // 响应处理：撤销后不再合格，无需检查结算冻结
        return QualificationResponseConverter.response(context.qualification(), QualificationDecisionSource.MANUAL.getCode(), now,
            Set.of());
    }

    /** 撤销的 MANUAL 决定：保留原有效开始时间，有效结束时间为撤销时间。 */
    QualificationDecision decision(RevokeQualificationRequest request, RoleQualification current, Instant now) {
        QualificationDecision decision = new QualificationDecision();
        decision.setDecisionId(ids.nextIdentifier());
        decision.setRoleType(current.getRoleType());
        decision.setRoleId(current.getRoleId());
        decision.setQualificationType(current.getQualificationType());
        decision.setDecisionSource(QualificationDecisionSource.MANUAL.getCode());
        decision.setOperatedBy(ReviewCodes.OPERATED_BY_PLATFORM);
        decision.setOutcome(QualificationStatus.REVOKED.getCode());
        decision.setOverrideReasonCode(request.getReasonCode());
        decision.setBasisNote(request.getBasisNote());
        decision.setValidFrom(current.getValidFrom());
        decision.setValidTo(now);
        decision.setDecidedBy(request.getActorId());
        decision.setDecidedTime(now);
        decision.setCreateTime(now);
        return decision;
    }
}
