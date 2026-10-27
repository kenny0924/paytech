package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_CHANGED;
import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_INVALID;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.repository.SettlementControlRepository;
import com.paytech.compliance.domainservice.service.QualificationService;
import com.paytech.compliance.service.processor.request.RecordManualQualificationRequest;
import com.paytech.compliance.service.processor.response.QualificationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 运营手工登记角色资格：结论固定为 APPROVED，复审状态 NOT_REQUIRED
 * <li>允许的组合：CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB、OPERATING_ENTITY + ENTITY_UNDERWRITING、LOCAL_PARTNER +
 * PARTNER_DUE_DILIGENCE；角色标识格式与有效期不合法时 422 QUALIFICATION_INVALID。compliance 不出站核对主体是否存在。</li>
 * <li>没有资格行时新建版本 1，此时不得携带 expectedVersion；已有资格行时必须携带页面读取的版本，不一致 409 QUALIFICATION_CHANGED。</li>
 * <li>一个事务写 MANUAL 决定（不关联案件）、新建或按版本条件切换投影、追加 OPERATIONS_MANUAL 状态变更；并发由版本条件与唯一键保证，
 * 不使用资源锁。没有业务操作身份，重复提交形成新的决定与新版本。</li>
 */
@Component
@AllArgsConstructor
public class RecordManualQualificationProcessor
    extends AbstractProcessor<RecordManualQualificationRequest, QualificationResponse> {
    /** 运营手工登记与撤销的触发类型。 */
    public static final String TRIGGER_TYPE = "OPERATIONS_MANUAL";

    private final Clock clock;
    private final ComplianceIdentifierGenerator ids;
    private final QualificationService qualificationService;
    private final RoleQualificationRepository qualifications;
    private final SettlementControlRepository settlementControls;

    /** 角色与资格类型组合、角色标识格式与有效期顺序。 */
    @Override
    protected void validate(RecordManualQualificationRequest request) {
        if (!request.getRoleType().allows(request.getQualificationType())) {
            throw new FailException(QUALIFICATION_INVALID, "角色不允许登记该资格类型: " + request.getRoleType().getCode() + "/"
                + request.getQualificationType().getCode());
        }
        if (!request.getRoleType().acceptsRoleId(request.getRoleId())) {
            throw new FailException(QUALIFICATION_INVALID,
                "角色标识格式无效: " + request.getRoleType().getCode() + "/" + request.getRoleId());
        }
        if (request.getValidTo() != null && !request.getValidTo().isAfter(request.getValidFrom())) {
            throw new FailException(QUALIFICATION_INVALID, "有效结束时间必须晚于有效开始时间");
        }
    }

    @Override
    protected QualificationResponse processRequest(RecordManualQualificationRequest request) {
        // 当前资格与页面版本
        RoleQualification current = qualifications.findByRole(request.getRoleType().getCode(), request.getRoleId(),
            request.getQualificationType().getCode());
        checkExpectedVersion(request, current);

        // 保存决定与投影
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        QualificationChangeContext context = QualificationChangeBuilder.change(ids, decision(request, now), current,
            request.getReasonCode(), TRIGGER_TYPE, null);
        qualificationService.saveChange(context);

        // 响应处理
        RoleQualification saved = context.qualification();
        return QualificationResponseConverter.response(saved, QualificationDecisionSource.MANUAL.getCode(), now, settlementControls
            .findBlockedClientIds(QualificationResponseConverter.clientIdsToCheck(List.of(saved), now), now));
    }

    /** 首次登记不得带版本；已有资格必须带与当前一致的版本。 */
    void checkExpectedVersion(RecordManualQualificationRequest request, RoleQualification current) {
        Long expected = request.getExpectedVersion();
        if (current == null && expected == null) {
            return;
        }
        if (current == null || !Objects.equals(expected, current.getVersion())) {
            throw new FailException(QUALIFICATION_CHANGED, "角色资格已被其他操作改变: " + request.getRoleType().getCode() + "/"
                + request.getRoleId() + "/" + request.getQualificationType().getCode() + ", 页面版本: " + expected);
        }
    }

    /** 手工登记的 MANUAL 决定：不关联案件，原因代码与依据说明随决定保存。 */
    QualificationDecision decision(RecordManualQualificationRequest request, Instant now) {
        QualificationDecision decision = new QualificationDecision();
        decision.setDecisionId(ids.nextIdentifier());
        decision.setRoleType(request.getRoleType().getCode());
        decision.setRoleId(request.getRoleId());
        decision.setQualificationType(request.getQualificationType().getCode());
        decision.setDecisionSource(QualificationDecisionSource.MANUAL.getCode());
        decision.setOperatedBy(ReviewCodes.OPERATED_BY_PLATFORM);
        decision.setOutcome(QualificationStatus.APPROVED.getCode());
        decision.setOverrideReasonCode(request.getReasonCode());
        decision.setBasisNote(request.getBasisNote());
        decision.setValidFrom(request.getValidFrom());
        decision.setValidTo(request.getValidTo());
        decision.setDecidedBy(request.getActorId());
        decision.setDecidedTime(now);
        decision.setCreateTime(now);
        return decision;
    }
}
