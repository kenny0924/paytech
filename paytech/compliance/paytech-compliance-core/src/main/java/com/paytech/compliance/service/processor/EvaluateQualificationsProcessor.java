package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_INVALID;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.domain.QualificationEvaluation;
import com.paytech.compliance.domain.QualificationSubject;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.repository.SettlementControlRepository;
import com.paytech.compliance.service.processor.request.EvaluateQualificationsRequest;
import com.paytech.compliance.service.processor.response.QualificationEvaluationResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 批量判定主体在查询时间是否具备交易所需的角色资格；只读
 * <li>每种角色要求的资格类型由 compliance 决定：CLIENT 为 INDIVIDUAL_KYC 或 ORGANIZATION_KYB 任一，OPERATING_ENTITY 为
 * ENTITY_UNDERWRITING，LOCAL_PARTNER 为 PARTNER_DUE_DILIGENCE。</li>
 * <li>有效期按查询时间实时判定，不依赖定时任务；资格合格的 Client 再检查生效中的 SETTLEMENT_BLOCK 结算控制。</li>
 * <li>不创建显式事务：每个主体的结论只依赖一次查询返回的行，资格与结算控制两次查询观察相邻的已提交状态在业务上可接受。</li>
 */
@Component
@AllArgsConstructor
public class EvaluateQualificationsProcessor
    extends AbstractProcessor<EvaluateQualificationsRequest, QualificationEvaluationResponse> {
    private final Clock clock;
    private final RoleQualificationRepository qualifications;
    private final SettlementControlRepository settlementControls;

    /** 角色标识必须符合各自角色的格式。 */
    @Override
    protected void validate(EvaluateQualificationsRequest request) {
        for (QualificationSubject subject : request.getSubjects()) {
            if (!subject.hasValidRoleId()) {
                throw new FailException(QUALIFICATION_INVALID,
                    "角色标识格式无效: " + subject.getRoleType().getCode() + "/" + subject.getRoleId());
            }
        }
    }

    @Override
    protected QualificationEvaluationResponse processRequest(EvaluateQualificationsRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<QualificationSubject> subjects = List.copyOf(new LinkedHashSet<>(request.getSubjects()));

        // 资格行
        Map<QualificationSubject, List<RoleQualification>> rows = qualifications.findBySubjects(subjects).stream()
            .collect(Collectors.groupingBy(row -> new QualificationSubject(QualificationRoleType.ofCode(row.getRoleType()),
                row.getRoleId())));
        List<QualificationEvaluation> evaluations = subjects.stream()
            .map(subject -> QualificationEvaluation.of(subject, rows.getOrDefault(subject, List.of()), now)).toList();

        // 合格 Client 的结算冻结
        Set<Long> clientIds = evaluations.stream().map(QualificationEvaluation::clientIdToCheck).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> blocked = settlementControls.findBlockedClientIds(clientIds, now);
        return new QualificationEvaluationResponse(now,
            evaluations.stream().map(item -> result(item.withSettlementBlocks(blocked))).toList());
    }

    /** 一个主体的判定结果；未登记时资格字段为空。 */
    static QualificationEvaluationResponse.Result result(QualificationEvaluation evaluation) {
        QualificationSubject subject = evaluation.getSubject();
        RoleQualification row = evaluation.getQualification();
        if (row == null) {
            return new QualificationEvaluationResponse.Result(subject.getRoleType().getCode(), subject.getRoleId(),
                evaluation.getStatus().getCode(), null, null, null, null);
        }
        return new QualificationEvaluationResponse.Result(subject.getRoleType().getCode(), subject.getRoleId(),
            evaluation.getStatus().getCode(), row.getQualificationType(), row.getQualificationId(), row.getValidFrom(),
            row.getValidTo());
    }
}
