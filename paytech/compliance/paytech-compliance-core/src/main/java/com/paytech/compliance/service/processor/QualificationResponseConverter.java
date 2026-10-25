package com.paytech.compliance.service.processor;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.domain.QualificationEvaluation;
import com.paytech.compliance.service.processor.response.QualificationResponse;

/** 资格投影到 Owner 结果的纯转换；列表与写命令共用。 */
final class QualificationResponseConverter {
    private QualificationResponseConverter() {
    }

    /**
     * 一条资格的 Owner 结果
     *
     * @param row 资格投影
     * @param decisionSource 当前决定来源
     * @param now 判定实时状态的时间
     * @param blockedClientIds 生效中结算冻结的 Client ID
     * @return Owner 结果
     */
    static QualificationResponse response(RoleQualification row, String decisionSource, Instant now, Set<Long> blockedClientIds) {
        return new QualificationResponse(row.getQualificationId(), row.getRoleType(), row.getRoleId(), row.getQualificationType(),
            row.getStatus(), QualificationEvaluation.effectiveStatus(row, now, blockedClientIds).getCode(), row.getValidFrom(),
            row.getValidTo(), row.getCurrentDecisionId(), decisionSource, row.getReviewStatus(), row.getVersion(),
            row.getUpdateTime());
    }

    /** 这些资格中需要检查结算冻结的 Client ID。 */
    static Set<Long> clientIdsToCheck(Collection<RoleQualification> rows, Instant now) {
        return rows.stream().map(row -> QualificationEvaluation.clientIdToCheck(row, now)).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }
}
