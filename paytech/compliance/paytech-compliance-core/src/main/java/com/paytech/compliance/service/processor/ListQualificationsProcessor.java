package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.QualificationErrorCode.QUALIFICATION_INVALID;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.domainservice.repository.SettlementControlRepository;
import com.paytech.compliance.service.processor.request.ListQualificationsRequest;
import com.paytech.compliance.service.processor.response.QualificationPageResponse;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 运营资格登记页的角色资格分页；只读，不创建显式事务
 * <li>按更新时间倒序、资格 ID 倒序稳定排序；每行附当前决定来源与查询时实时判定的状态（含 Client 结算冻结）。</li>
 */
@Component
@AllArgsConstructor
public class ListQualificationsProcessor extends AbstractProcessor<ListQualificationsRequest, QualificationPageResponse> {
    private final Clock clock;
    private final RoleQualificationRepository qualifications;
    private final QualificationDecisionRepository decisions;
    private final SettlementControlRepository settlementControls;

    /** roleId 必须与 roleType 一起提供，且符合该角色的格式。 */
    @Override
    protected void validate(ListQualificationsRequest request) {
        if (request.getRoleId() == null) {
            return;
        }
        if (request.getRoleType() == null || !request.getRoleType().acceptsRoleId(request.getRoleId())) {
            throw new FailException(QUALIFICATION_INVALID, "角色标识必须与角色类型一起提供且格式有效: " + request.getRoleId());
        }
    }

    @Override
    protected QualificationPageResponse processRequest(ListQualificationsRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String roleType = CodeEnum.defaultCode(request.getRoleType());
        String qualificationType = CodeEnum.defaultCode(request.getQualificationType());
        String status = CodeEnum.defaultCode(request.getStatus());
        List<RoleQualification> rows = qualifications.page(roleType, request.getRoleId(), qualificationType, status,
            request.getOffset(), request.getSize());
        long total = qualifications.count(roleType, request.getRoleId(), qualificationType, status);

        // 当前决定来源与 Client 结算冻结
        Map<Long, String> sources = decisions.findByIds(rows.stream().map(RoleQualification::getCurrentDecisionId).toList())
            .stream().collect(Collectors.toMap(QualificationDecision::getDecisionId, QualificationDecision::getDecisionSource));
        Set<Long> blocked = settlementControls.findBlockedClientIds(QualificationResponseConverter.clientIdsToCheck(rows, now), now);
        return new QualificationPageResponse(rows.stream().map(row -> QualificationResponseConverter.response(row,
            sources.get(row.getCurrentDecisionId()), now, blocked)).toList(), request.getPage(), request.getSize(), total);
    }
}
